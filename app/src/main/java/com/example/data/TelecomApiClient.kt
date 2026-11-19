package com.example.data

import android.annotation.SuppressLint
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

sealed class InquiryStartOutcome {
    data object NeedsOtp : InquiryStartOutcome()
    data class DirectResult(val result: BalanceInquiryResult) : InquiryStartOutcome()
    data class Error(val message: String) : InquiryStartOutcome()
}

sealed class InquiryFetchOutcome {
    data class Success(val result: BalanceInquiryResult) : InquiryFetchOutcome()
    data object NeedsReauth : InquiryFetchOutcome()
    data class Error(val message: String) : InquiryFetchOutcome()
}

class TelecomApiClient(private val prefs: AppPreferences) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client: OkHttpClient = buildHttpClient()

    private fun buildHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

        // Telecom gateways on direct IP (82.114.168.238:6321) and custom ports (ana2.sabafon.net.ye:20005)
        // frequently use private/self-signed TLS certificates.
        try {
            val trustManager = @SuppressLint("CustomX509TrustManager") object : X509TrustManager {
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            builder.hostnameVerifier { hostname, _ ->
                hostname == "82.114.168.238" || hostname.endsWith("sabafon.net.ye")
            }
        } catch (_: Throwable) {
        }

        return builder.build()
    }

    // ══════════════════════════════════════════════════════════════════════
    // YEMEN MOBILE (https://82.114.168.238:6321)
    // 1. POST /user/register   {"phoneNumber":"77xxxxxxx","password":"77xxxxxxx"}
    // 2. PATCH /User/verify    {"phoneNumber":"77xxxxxxx","code":"...","password":"77xxxxxxx"}
    // 3. POST /User/login      {"phoneNumber":"77xxxxxxx","password":"77xxxxxxx"} -> token
    // 4. GET /v1/Account       (Bearer token) -> balance + offers
    // ══════════════════════════════════════════════════════════════════════

    fun startInquiry(phone: String, carrier: CarrierType): InquiryStartOutcome {
        return when (carrier) {
            CarrierType.YEMEN_MOBILE -> startYemenMobileInquiry(phone)
            CarrierType.SABAFON -> startSabafonInquiry(phone)
        }
    }

    private fun startYemenMobileInquiry(phone: String): InquiryStartOutcome {
        // Step 1: POST /user/register {"phoneNumber":"77xxxxxxx","password":"77xxxxxxx"}
        val registerBody = JSONObject().apply {
            put("phoneNumber", phone)
            put("password", phone)
        }.toString()

        val registerResp = executeRequest(
            method = "POST",
            url = "$YEMEN_MOBILE_BASE/user/register",
            jsonBody = registerBody
        )

        if (registerResp.networkError != null) {
            return InquiryStartOutcome.Error("تعذر الاتصال بخادم يمن موبايل، تأكد من اتصال الإنترنت وحاول مجدداً")
        }

        if (registerResp.code in 200..299) {
            // SMS OTP sent successfully
            return InquiryStartOutcome.NeedsOtp
        }

        // If number is already registered/verified, silently authenticate and fetch balance directly
        val silentToken = silentYemenMobileAuth(phone)
        if (!silentToken.isNullOrBlank()) {
            prefs.saveToken(phone, silentToken)
            val fetch = fetchYemenMobileBalance(phone, silentToken)
            if (fetch is InquiryFetchOutcome.Success) {
                return InquiryStartOutcome.DirectResult(fetch.result)
            }
        }

        // If register indicated OTP already pending or existing unverified number, proceed to Screen 2
        if (registerResp.code in listOf(400, 409, 422)) {
            return InquiryStartOutcome.NeedsOtp
        }

        return InquiryStartOutcome.Error(
            sanitizeServerError(registerResp.body, "تعذر إرسال رمز التحقق حالياً، حاول مرة أخرى")
        )
    }

    fun verifyOtpAndFetch(phone: String, carrier: CarrierType, otpCode: String): InquiryFetchOutcome {
        return when (carrier) {
            CarrierType.YEMEN_MOBILE -> verifyYemenMobileOtpAndFetch(phone, otpCode)
            CarrierType.SABAFON -> verifySabafonOtpAndFetch(phone, otpCode)
        }
    }

    private fun verifyYemenMobileOtpAndFetch(phone: String, otpCode: String): InquiryFetchOutcome {
        // Step 2: PATCH /User/verify {"phoneNumber":"77xxxxxxx","code":"الرمز","password":"77xxxxxxx"}
        val verifyPayload = JSONObject().apply {
            put("phoneNumber", phone)
            put("code", otpCode.trim())
            put("password", phone)
        }.toString()

        val verifyResp = executeRequest(
            method = "PATCH",
            url = "$YEMEN_MOBILE_BASE/User/verify",
            jsonBody = verifyPayload
        )

        if (verifyResp.networkError != null) {
            return InquiryFetchOutcome.Error("تعذر الاتصال بالخادم أثناء التحقق من الرمز")
        }

        if (verifyResp.code !in 200..299) {
            return InquiryFetchOutcome.Error(
                sanitizeServerError(verifyResp.body, "رمز التحقق غير صحيح أو منتهي الصلاحية")
            )
        }

        // Step 3: Silent POST /User/login {"phoneNumber":"77xxxxxxx","password":"77xxxxxxx"} -> token
        val token = extractTokenFromBody(verifyResp.body) ?: silentYemenMobileAuth(phone)
        if (token.isNullOrBlank()) {
            return InquiryFetchOutcome.Error("تعذر إكمال التحقق مع الخادم، يرجى المحاولة مرة أخرى")
        }

        prefs.saveToken(phone, token)

        // Step 4: GET /v1/Account (Bearer token)
        return fetchYemenMobileBalance(phone, token)
    }

    private fun silentYemenMobileAuth(phone: String): String? {
        val payload = JSONObject().apply {
            put("phoneNumber", phone)
            put("password", phone)
        }.toString()

        val resp = executeRequest(
            method = "POST",
            url = "$YEMEN_MOBILE_BASE/User/login",
            jsonBody = payload
        )
        if (resp.code in 200..299) {
            return extractTokenFromBody(resp.body)
        }
        return null
    }

    private fun fetchYemenMobileBalance(phone: String, token: String): InquiryFetchOutcome {
        val resp = executeRequest(
            method = "GET",
            url = "$YEMEN_MOBILE_BASE/v1/Account",
            bearerToken = token
        )

        if (resp.networkError != null) {
            return InquiryFetchOutcome.Error("تعذر جلب بيانات الرصيد، يرجى التحقق من اتصال الإنترنت")
        }

        if (resp.code == 401 || resp.code == 403) {
            // Silently refresh token once
            val refreshed = silentYemenMobileAuth(phone)
            if (!refreshed.isNullOrBlank()) {
                prefs.saveToken(phone, refreshed)
                val retry = executeRequest(
                    method = "GET",
                    url = "$YEMEN_MOBILE_BASE/v1/Account",
                    bearerToken = refreshed
                )
                if (retry.code in 200..299) {
                    return InquiryFetchOutcome.Success(parseBalanceJson(retry.body, phone, CarrierType.YEMEN_MOBILE, false))
                }
            }
            prefs.clearToken(phone)
            return InquiryFetchOutcome.NeedsReauth
        }

        if (resp.code in 200..299 && resp.body.isNotBlank()) {
            return InquiryFetchOutcome.Success(
                parseBalanceJson(resp.body, phone, CarrierType.YEMEN_MOBILE, false)
            )
        }

        return InquiryFetchOutcome.Error(
            sanitizeServerError(resp.body, "تعذر قراءة تفاصيل الرصيد والباقات حالياً")
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // SABAFON (https://ana2.sabafon.net.ye:20005)
    // 1. POST /ana-sabafon-api-auth/Authenticate/SendOtp  {"GSM":"96771xxxxxxx"}
    // 2. Verify OTP -> extract token -> query Balance + Active Packages
    // ══════════════════════════════════════════════════════════════════════

    private fun startSabafonInquiry(phone: String): InquiryStartOutcome {
        val gsm = formatSabafonGsm(phone)
        val payload = JSONObject().apply {
            put("GSM", gsm)
        }.toString()

        val resp = executeRequest(
            method = "POST",
            url = "$SABAFON_BASE/ana-sabafon-api-auth/Authenticate/SendOtp",
            jsonBody = payload
        )

        if (resp.networkError != null) {
            return InquiryStartOutcome.Error("تعذر الاتصال بخادم سبأفون، تأكد من اتصال الإنترنت وحاول مجدداً")
        }

        if (resp.code in 200..299) {
            return InquiryStartOutcome.NeedsOtp
        }

        return InquiryStartOutcome.Error(
            sanitizeServerError(resp.body, "تعذر إرسال رمز التحقق لرقم سبأفون، تأكد من الرقم وحاول مجدداً")
        )
    }

    private fun verifySabafonOtpAndFetch(phone: String, otpCode: String): InquiryFetchOutcome {
        val gsm = formatSabafonGsm(phone)
        val cleanOtp = otpCode.trim()

        val verifyPayload = JSONObject().apply {
            put("GSM", gsm)
            put("gsm", gsm)
            put("OTP", cleanOtp)
            put("otp", cleanOtp)
            put("Code", cleanOtp)
            put("code", cleanOtp)
        }.toString()

        // Try configured primary path first, then standard Ana Sabafon auth endpoints if 404/405
        val configuredVerify = normalizePath(prefs.getSabafonVerifyPath())
        val candidateVerifyPaths = linkedSetOf(
            configuredVerify,
            "/ana-sabafon-api-auth/Authenticate/VerifyOtp",
            "/ana-sabafon-api-auth/Authenticate/ValidateOtp",
            "/ana-sabafon-api-auth/Authenticate/ConfirmOtp",
            "/ana-sabafon-api-auth/Authenticate/OtpLogin",
            "/ana-sabafon-api-auth/Authenticate/AuthenticateOtp"
        )

        var lastResp: RawHttpResponse? = null
        var verifiedBody: String? = null

        for (path in candidateVerifyPaths) {
            val resp = executeRequest(
                method = "POST",
                url = "$SABAFON_BASE$path",
                jsonBody = verifyPayload
            )
            lastResp = resp
            if (resp.networkError != null) {
                return InquiryFetchOutcome.Error("تعذر الاتصال بخادم سبأفون أثناء التحقق من الرمز")
            }
            if (resp.code in 200..299) {
                verifiedBody = resp.body
                break
            }
            // If server rejected the OTP itself (400/401/403), stop trying other paths
            if (resp.code in listOf(400, 401, 403, 422)) {
                return InquiryFetchOutcome.Error(
                    sanitizeServerError(resp.body, "رمز التحقق غير صحيح أو منتهي الصلاحية")
                )
            }
        }

        if (verifiedBody == null) {
            return InquiryFetchOutcome.Error(
                sanitizeServerError(lastResp?.body.orEmpty(), "تعذر التحقق من الرمز مع خادم سبأفون حالياً")
            )
        }

        val token = extractTokenFromBody(verifiedBody)
        if (!token.isNullOrBlank()) {
            prefs.saveToken(phone, token)
            val fetched = fetchSabafonBalanceAndPackages(phone, token)
            if (fetched is InquiryFetchOutcome.Success) {
                return fetched
            }
        }

        // If verify response itself already contains balance/account data, parse it directly
        if (containsBalanceFields(verifiedBody)) {
            return InquiryFetchOutcome.Success(
                parseBalanceJson(verifiedBody, phone, CarrierType.SABAFON, false)
            )
        }

        return InquiryFetchOutcome.Error("تم التحقق من الرمز ولكن تعذر جلب تفاصيل الرصيد والباقات من خادم سبأفون")
    }

    private fun fetchSabafonBalanceAndPackages(phone: String, token: String): InquiryFetchOutcome {
        val gsm = formatSabafonGsm(phone)
        val configuredBalance = normalizePath(prefs.getSabafonBalancePath())
        val balancePaths = linkedSetOf(
            configuredBalance,
            "/ana-sabafon-api-subscriber/Subscriber/GetBalance",
            "/ana-sabafon-api-subscriber/Subscriber/GetSubscriberInfo",
            "/ana-sabafon-api-subscriber/Subscriber/GetInfo",
            "/ana-sabafon-api-subscriber/Balance/GetBalance",
            "/ana-sabafon-api/Subscriber/GetBalance",
            "/ana-sabafon-api-account/Account/GetBalance",
            "/api/Subscriber/GetBalance"
        )

        var balanceResult: BalanceInquiryResult? = null
        var authExpired = false

        for (path in balancePaths) {
            val getResp = executeRequest(
                method = "GET",
                url = "$SABAFON_BASE$path?GSM=$gsm",
                bearerToken = token
            )
            if (getResp.code == 401 || getResp.code == 403) {
                authExpired = true
                break
            }
            if (getResp.code in 200..299 && getResp.body.isNotBlank()) {
                balanceResult = parseBalanceJson(getResp.body, phone, CarrierType.SABAFON, false)
                break
            }
            // Some ASP.NET endpoints expect POST with {"GSM": "..."}
            if (getResp.code == 405 || getResp.code == 400) {
                val postResp = executeRequest(
                    method = "POST",
                    url = "$SABAFON_BASE$path",
                    jsonBody = JSONObject().put("GSM", gsm).toString(),
                    bearerToken = token
                )
                if (postResp.code == 401 || postResp.code == 403) {
                    authExpired = true
                    break
                }
                if (postResp.code in 200..299 && postResp.body.isNotBlank()) {
                    balanceResult = parseBalanceJson(postResp.body, phone, CarrierType.SABAFON, false)
                    break
                }
            }
        }

        if (authExpired) {
            prefs.clearToken(phone)
            return InquiryFetchOutcome.NeedsReauth
        }

        // Fetch active packages for Sabafon and merge with balance result
        val packagesList = fetchSabafonActivePackages(gsm, token)

        val finalResult = balanceResult?.let { base ->
            val mergedOffers = if (base.offers.isNotEmpty()) {
                base.offers
            } else {
                packagesList
            }
            base.copy(offers = mergedOffers)
        }

        return if (finalResult != null) {
            InquiryFetchOutcome.Success(finalResult)
        } else if (packagesList.isNotEmpty()) {
            InquiryFetchOutcome.Success(
                BalanceInquiryResult(
                    carrier = CarrierType.SABAFON,
                    phoneNumber = phone,
                    subscriberName = "مشترك سبأفون",
                    payingMethod = "PrePaid",
                    status = "نشط",
                    balanceAmount = 0.0,
                    offers = packagesList,
                    isDemo = false
                )
            )
        } else {
            InquiryFetchOutcome.Error("تعذر قراءة بيانات الرصيد والباقات من خادم سبأفون")
        }
    }

    private fun fetchSabafonActivePackages(gsm: String, token: String): List<ActivePackage> {
        val configuredPkg = normalizePath(prefs.getSabafonPackagesPath())
        val packagePaths = linkedSetOf(
            configuredPkg,
            "/ana-sabafon-api-subscriber/Subscriber/GetActivePackages",
            "/ana-sabafon-api-subscriber/Subscriber/GetSubscribedPackages",
            "/ana-sabafon-api-subscriber/Subscriber/GetOffers",
            "/ana-sabafon-api-packages/Packages/GetActivePackages",
            "/ana-sabafon-api-packages/Packages/GetSubscribedPackages",
            "/ana-sabafon-api/Packages/GetActivePackages"
        )

        for (path in packagePaths) {
            val getResp = executeRequest(
                method = "GET",
                url = "$SABAFON_BASE$path?GSM=$gsm",
                bearerToken = token
            )
            if (getResp.code in 200..299 && getResp.body.isNotBlank()) {
                val parsed = extractPackagesFromRawBody(getResp.body)
                if (parsed.isNotEmpty()) return parsed
            }
            if (getResp.code == 405 || getResp.code == 400) {
                val postResp = executeRequest(
                    method = "POST",
                    url = "$SABAFON_BASE$path",
                    jsonBody = JSONObject().put("GSM", gsm).toString(),
                    bearerToken = token
                )
                if (postResp.code in 200..299 && postResp.body.isNotBlank()) {
                    val parsed = extractPackagesFromRawBody(postResp.body)
                    if (parsed.isNotEmpty()) return parsed
                }
            }
        }
        return emptyList()
    }

    fun refreshBalance(phone: String, carrier: CarrierType): InquiryFetchOutcome {
        val cachedToken = prefs.getToken(phone)
        return when (carrier) {
            CarrierType.YEMEN_MOBILE -> {
                val token = cachedToken ?: silentYemenMobileAuth(phone)
                if (token.isNullOrBlank()) {
                    InquiryFetchOutcome.NeedsReauth
                } else {
                    prefs.saveToken(phone, token)
                    fetchYemenMobileBalance(phone, token)
                }
            }
            CarrierType.SABAFON -> {
                if (cachedToken.isNullOrBlank()) {
                    InquiryFetchOutcome.NeedsReauth
                } else {
                    fetchSabafonBalanceAndPackages(phone, cachedToken)
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DEMO MODE (Zero Internet - Exact Payloads)
    // ══════════════════════════════════════════════════════════════════════

    fun buildDemoResult(phone: String, carrier: CarrierType): BalanceInquiryResult {
        val demoJson = when (carrier) {
            CarrierType.YEMEN_MOBILE -> """
                {
                  "name": "عزيزي المشترك",
                  "payingMethod": "PrePaid",
                  "amount": 125.75,
                  "status": "نشط",
                  "offers": [
                    {
                      "packageName": "باقة مزايا 500",
                      "price": 500,
                      "remaining": "350 دقيقة",
                      "expiryDate": "2026/12/15"
                    }
                  ]
                }
            """.trimIndent()
            CarrierType.SABAFON -> {
                val displayMsisdn = if (phone.length == 9 && phone.startsWith("71")) phone else "711234567"
                """
                {
                  "currentBalance": 87.25,
                  "msisdn": "$displayMsisdn",
                  "payingMethod": "PrePaid",
                  "status": "نشط",
                  "offers": [
                    {
                      "packageName": "باقة سوبر نت 4G",
                      "price": 2800,
                      "remaining": "6.4 جيجابايت",
                      "expiryDate": "2026/12/20"
                    },
                    {
                      "packageName": "باقة يلا الأسبوعية",
                      "price": 750,
                      "remaining": "180 دقيقة",
                      "expiryDate": "2026/11/28"
                    }
                  ]
                }
                """.trimIndent()
            }
        }
        return parseBalanceJson(demoJson, phone, carrier, isDemo = true)
    }

    // ══════════════════════════════════════════════════════════════════════
    // RESILIENT JSON PARSERS
    // ══════════════════════════════════════════════════════════════════════

    fun parseBalanceJson(
        rawBody: String,
        fallbackPhone: String,
        carrier: CarrierType,
        isDemo: Boolean
    ): BalanceInquiryResult {
        val trimmed = rawBody.trim()
        val root = try {
            JSONObject(trimmed)
        } catch (_: Throwable) {
            JSONObject()
        }

        // Unwrap nested "data" / "result" / "account" if present
        val dataObj = root.optJSONObject("data")
            ?: root.optJSONObject("result")
            ?: root.optJSONObject("account")
            ?: root

        val balance = extractDouble(
            dataObj, root,
            keys = listOf("amount", "currentBalance", "balance", "mainBalance", "credit", "availableBalance")
        ) ?: 0.0

        val payingMethod = extractString(
            dataObj, root,
            keys = listOf("payingMethod", "paymentType", "subscriberType", "lineType", "tariffType")
        ).ifBlank { "PrePaid" }

        val msisdn = extractString(
            dataObj, root,
            keys = listOf("msisdn", "phoneNumber", "gsm", "GSM", "mobile", "number")
        ).let { CarrierType.normalizeDigits(it) }.ifBlank { fallbackPhone }

        val subscriberName = extractString(
            dataObj, root,
            keys = listOf("name", "subscriberName", "customerName", "fullName")
        ).ifBlank {
            if (carrier == CarrierType.YEMEN_MOBILE) "عزيزي المشترك" else "مشترك سبأفون"
        }

        val status = extractString(
            dataObj, root,
            keys = listOf("status", "lineStatus", "state", "serviceStatus")
        ).ifBlank { "نشط" }

        val offers = extractPackagesFromObject(dataObj).ifEmpty {
            extractPackagesFromObject(root)
        }

        return BalanceInquiryResult(
            carrier = carrier,
            phoneNumber = msisdn,
            subscriberName = subscriberName,
            payingMethod = payingMethod,
            status = translateStatus(status),
            balanceAmount = balance,
            offers = offers,
            isDemo = isDemo
        )
    }

    private fun extractPackagesFromRawBody(rawBody: String): List<ActivePackage> {
        val trimmed = rawBody.trim()
        return try {
            if (trimmed.startsWith("[")) {
                parsePackagesArray(JSONArray(trimmed))
            } else {
                val obj = JSONObject(trimmed)
                val nested = obj.optJSONObject("data") ?: obj.optJSONObject("result")
                if (nested != null) {
                    extractPackagesFromObject(nested).ifEmpty { extractPackagesFromObject(obj) }
                } else {
                    val dataArr = obj.optJSONArray("data") ?: obj.optJSONArray("result")
                    if (dataArr != null) parsePackagesArray(dataArr) else extractPackagesFromObject(obj)
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun extractPackagesFromObject(obj: JSONObject): List<ActivePackage> {
        val arrayKeys = listOf("offers", "packages", "activePackages", "bundles", "subscriptions", "services", "items")
        for (key in arrayKeys) {
            val arr = obj.optJSONArray(key)
            if (arr != null && arr.length() > 0) {
                return parsePackagesArray(arr)
            }
        }
        return emptyList()
    }

    private fun parsePackagesArray(arr: JSONArray): List<ActivePackage> {
        val result = mutableListOf<ActivePackage>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val name = extractString(
                item, null,
                keys = listOf("packageName", "offerName", "name", "bundleName", "title", "serviceName", "arabicName")
            )
            if (name.isBlank()) continue

            val remaining = extractString(
                item, null,
                keys = listOf("remaining", "remainingBalance", "remainingQuota", "remainingUnits", "balance", "left", "quota")
            ).ifBlank { "فعالة" }

            val expiry = extractString(
                item, null,
                keys = listOf("expiryDate", "expireDate", "endDate", "validUntil", "expirationDate", "validity")
            )

            val priceVal = extractString(
                item, null,
                keys = listOf("price", "cost", "amount")
            )

            result.add(
                ActivePackage(
                    packageName = name,
                    remaining = remaining,
                    expiryDate = expiry,
                    price = priceVal
                )
            )
        }
        return result
    }

    private fun extractTokenFromBody(rawBody: String): String? {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            val unquoted = trimmed.removeSurrounding("\"")
            return if (unquoted.length >= 16) unquoted else null
        }
        return try {
            val root = JSONObject(trimmed)
            val dataObj = root.optJSONObject("data") ?: root.optJSONObject("result")
            val tokenKeys = listOf("token", "accessToken", "access_token", "jwt", "jwtToken", "bearerToken", "idToken")
            for (key in tokenKeys) {
                val direct = root.optString(key, "").trim()
                if (direct.isNotBlank() && direct != "null") return direct
                val nested = dataObj?.optString(key, "")?.trim().orEmpty()
                if (nested.isNotBlank() && nested != "null") return nested
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun containsBalanceFields(rawBody: String): Boolean {
        val lower = rawBody.lowercase()
        return lower.contains("amount") ||
            lower.contains("currentbalance") ||
            lower.contains("\"balance\"") ||
            lower.contains("offers") ||
            lower.contains("packages")
    }

    private fun extractDouble(primary: JSONObject, fallback: JSONObject, keys: List<String>): Double? {
        for (key in keys) {
            if (primary.has(key) && !primary.isNull(key)) {
                val raw = primary.opt(key)?.toString()?.trim().orEmpty()
                raw.toDoubleOrNull()?.let { return it }
            }
            if (fallback.has(key) && !fallback.isNull(key)) {
                val raw = fallback.opt(key)?.toString()?.trim().orEmpty()
                raw.toDoubleOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun extractString(primary: JSONObject, fallback: JSONObject?, keys: List<String>): String {
        for (key in keys) {
            if (primary.has(key) && !primary.isNull(key)) {
                val value = primary.optString(key, "").trim()
                if (value.isNotBlank() && value != "null") return value
            }
            if (fallback != null && fallback.has(key) && !fallback.isNull(key)) {
                val value = fallback.optString(key, "").trim()
                if (value.isNotBlank() && value != "null") return value
            }
        }
        return ""
    }

    private fun translateStatus(raw: String): String {
        return when (raw.trim().lowercase()) {
            "active", "enabled", "ok", "valid", "open" -> "نشط"
            "inactive", "suspended", "blocked", "expired" -> "موقوف"
            "grace", "grace_period" -> "فترة سماح"
            else -> raw
        }
    }

    private fun sanitizeServerError(body: String, fallbackArabic: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return fallbackArabic
        val extracted = try {
            val obj = JSONObject(trimmed)
            obj.optString("message", "").ifBlank {
                obj.optString("error", "").ifBlank {
                    obj.optString("title", "")
                }
            }
        } catch (_: Throwable) {
            ""
        }
        if (extracted.isBlank()) return fallbackArabic
        val lower = extracted.lowercase()
        // Strictly hide forbidden terms ("password", "login", "account", "حساب") from user-facing copy
        if (lower.contains("password") || lower.contains("login") || lower.contains("account") || extracted.contains("حساب")) {
            return fallbackArabic
        }
        return extracted.take(140)
    }

    private fun formatSabafonGsm(phone: String): String {
        val normalized = CarrierType.normalizeDigits(phone)
        return if (normalized.startsWith("967")) normalized else "967$normalized"
    }

    private fun normalizePath(path: String): String {
        val trimmed = path.trim()
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    private data class RawHttpResponse(
        val code: Int = -1,
        val body: String = "",
        val networkError: Throwable? = null
    )

    private fun executeRequest(
        method: String,
        url: String,
        jsonBody: String? = null,
        bearerToken: String? = null
    ): RawHttpResponse {
        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")

            if (!bearerToken.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $bearerToken")
            }

            when (method.uppercase()) {
                "GET" -> reqBuilder.get()
                "POST" -> reqBuilder.post((jsonBody ?: "{}").toRequestBody(jsonMediaType))
                "PATCH" -> reqBuilder.patch((jsonBody ?: "{}").toRequestBody(jsonMediaType))
                else -> reqBuilder.method(method, jsonBody?.toRequestBody(jsonMediaType))
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                RawHttpResponse(code = response.code, body = respBody)
            }
        } catch (t: Throwable) {
            RawHttpResponse(networkError = t)
        }
    }

    companion object {
        const val YEMEN_MOBILE_BASE = "https://82.114.168.238:6321"
        const val SABAFON_BASE = "https://ana2.sabafon.net.ye:20005"
    }
}
