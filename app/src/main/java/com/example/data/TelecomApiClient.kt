package com.example.data

import android.annotation.SuppressLint
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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
    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()
    private val client: OkHttpClient = buildHttpClient()

    @Volatile
    private var lastSabafonRequestMillis: Long = 0L

    @Volatile
    private var sabafonRateLimitedUntilMillis: Long = 0L

    @Volatile
    var lastDiagnosticLog: String = "لا توجد عمليات مسجلة بعد"
        private set

    private val traceHistory = ArrayDeque<String>(12)

    private fun recordTrace(line: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        synchronized(traceHistory) {
            if (traceHistory.size >= 10) {
                traceHistory.removeFirst()
            }
            traceHistory.addLast("[$ts] $line")
            lastDiagnosticLog = traceHistory.joinToString("\n")
        }
    }

    private fun buildHttpClient(): OkHttpClient {
        val inMemoryCookieJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                if (cookies.isNotEmpty()) {
                    cookieStore[url.host] = cookies
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host].orEmpty()
            }
        }

        val builder = OkHttpClient.Builder()
            .cookieJar(inMemoryCookieJar)
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

        if (registerResp.code == 429) {
            return InquiryStartOutcome.Error(buildRateLimitArabicMessage(registerResp.retryAfterSeconds))
        }

        if (registerResp.code in 200..299) {
            return InquiryStartOutcome.NeedsOtp
        }

        // If already registered & verified, silently obtain token and fetch balance
        val silentToken = silentYemenMobileAuth(phone)
        if (!silentToken.isNullOrBlank()) {
            prefs.saveToken(phone, silentToken)
            val fetch = fetchYemenMobileBalance(phone, silentToken)
            if (fetch is InquiryFetchOutcome.Success) {
                return InquiryStartOutcome.DirectResult(fetch.result)
            }
        }

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

        if (verifyResp.code == 429) {
            return InquiryFetchOutcome.Error(buildRateLimitArabicMessage(verifyResp.retryAfterSeconds))
        }

        if (verifyResp.code !in 200..299) {
            return InquiryFetchOutcome.Error(
                sanitizeServerError(verifyResp.body, "رمز التحقق غير صحيح أو منتهي الصلاحية")
            )
        }

        val token = extractTokenFromBody(verifyResp.body) ?: silentYemenMobileAuth(phone)
        if (token.isNullOrBlank()) {
            return InquiryFetchOutcome.Error("تعذر إكمال التحقق مع الخادم، يرجى المحاولة مرة أخرى")
        }

        prefs.saveToken(phone, token)
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

        if (resp.code == 429) {
            return InquiryFetchOutcome.Error(buildRateLimitArabicMessage(resp.retryAfterSeconds))
        }

        if (resp.code == 401 || resp.code == 403) {
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
    // Strict anti-rate-limit design:
    // - NEVER sprays multiple guessed URLs in a loop (prevents Kong 429 "API rate limit exceeded")
    // - Enforces polite spacing between requests & respects Retry-After
    // - Sends clean single-request calls to configured Verify / Balance / Packages endpoints
    // ══════════════════════════════════════════════════════════════════════

    private fun checkSabafonCooldown(): String? {
        val now = System.currentTimeMillis()
        if (now < sabafonRateLimitedUntilMillis) {
            val waitSec = ((sabafonRateLimitedUntilMillis - now) / 1000L).coerceAtLeast(1L)
            return "تم تجاوز حد الطلبات المؤقت من خادم سبأفون، يرجى الانتظار $waitSec ثانية ثم المحاولة مجدداً"
        }
        return null
    }

    private fun ensureSabafonPacing() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastSabafonRequestMillis
        if (lastSabafonRequestMillis > 0L && elapsed < SABAFON_MIN_PACING_MS) {
            try {
                Thread.sleep(SABAFON_MIN_PACING_MS - elapsed)
            } catch (_: InterruptedException) {
            }
        }
        lastSabafonRequestMillis = System.currentTimeMillis()
    }

    private fun handleSabafonRateLimit(retryAfterSeconds: Int?): String {
        val cooldownSec = (retryAfterSeconds ?: 30).coerceIn(10, 120)
        sabafonRateLimitedUntilMillis = System.currentTimeMillis() + (cooldownSec * 1000L)
        return buildRateLimitArabicMessage(cooldownSec)
    }

    private fun startSabafonInquiry(phone: String): InquiryStartOutcome {
        checkSabafonCooldown()?.let { return InquiryStartOutcome.Error(it) }

        // If we already hold a valid cached bearer token for this Sabafon line, try fetching directly
        // to save an unnecessary SendOtp call against Kong's rate limit.
        val existingToken = prefs.getToken(phone)
        if (!existingToken.isNullOrBlank()) {
            val directFetch = fetchSabafonBalanceAndPackages(phone, existingToken)
            if (directFetch is InquiryFetchOutcome.Success) {
                return InquiryStartOutcome.DirectResult(directFetch.result)
            }
            if (directFetch is InquiryFetchOutcome.Error && System.currentTimeMillis() < sabafonRateLimitedUntilMillis) {
                return InquiryStartOutcome.Error(directFetch.message)
            }
        }

        ensureSabafonPacing()

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

        if (resp.code == 429) {
            return InquiryStartOutcome.Error(handleSabafonRateLimit(resp.retryAfterSeconds))
        }

        if (resp.code in 200..299) {
            return InquiryStartOutcome.NeedsOtp
        }

        return InquiryStartOutcome.Error(
            sanitizeServerError(resp.body, "تعذر إرسال رمز التحقق لرقم سبأفون، تأكد من الرقم وحاول مجدداً")
        )
    }

    private fun verifySabafonOtpAndFetch(phone: String, otpCode: String): InquiryFetchOutcome {
        checkSabafonCooldown()?.let { return InquiryFetchOutcome.Error(it) }
        ensureSabafonPacing()

        val gsm = formatSabafonGsm(phone)
        val cleanOtp = otpCode.trim()

        // Clean exact JSON matching Sabafon's PascalCase convention (no conflicting duplicate keys)
        val verifyPayload = JSONObject().apply {
            put("GSM", gsm)
            put("OTP", cleanOtp)
        }.toString()

        val verifyPath = normalizePath(prefs.getSabafonVerifyPath())
        val resp = executeRequest(
            method = "POST",
            url = "$SABAFON_BASE$verifyPath",
            jsonBody = verifyPayload
        )

        if (resp.networkError != null) {
            return InquiryFetchOutcome.Error("تعذر الاتصال بخادم سبأفون أثناء التحقق من الرمز")
        }

        if (resp.code == 429) {
            return InquiryFetchOutcome.Error(handleSabafonRateLimit(resp.retryAfterSeconds))
        }

        if (resp.code == 404 || resp.code == 405) {
            return InquiryFetchOutcome.Error(
                "مسار التحقق غير مطابق على خادم سبأفون (${resp.code}) — يمكنك ضبط المسار الصحيح من قائمة المطور (5 نقرات على الإصدار)"
            )
        }

        if (resp.code !in 200..299) {
            return InquiryFetchOutcome.Error(
                sanitizeServerError(resp.body, "رمز التحقق غير صحيح أو منتهي الصلاحية")
            )
        }

        val token = extractTokenFromBody(resp.body)
        if (!token.isNullOrBlank()) {
            prefs.saveToken(phone, token)
            // If verify response already included full balance & offers, return immediately without extra call!
            if (containsBalanceFields(resp.body)) {
                return InquiryFetchOutcome.Success(
                    parseBalanceJson(resp.body, phone, CarrierType.SABAFON, false)
                )
            }
            return fetchSabafonBalanceAndPackages(phone, token)
        }

        if (containsBalanceFields(resp.body)) {
            return InquiryFetchOutcome.Success(
                parseBalanceJson(resp.body, phone, CarrierType.SABAFON, false)
            )
        }

        return InquiryFetchOutcome.Error("تم استلام رد من سبأفون بدون رمز جلسة — راجع سجل التشخيص في قائمة المطور")
    }

    private fun fetchSabafonBalanceAndPackages(phone: String, token: String): InquiryFetchOutcome {
        checkSabafonCooldown()?.let { return InquiryFetchOutcome.Error(it) }
        ensureSabafonPacing()

        val gsm = formatSabafonGsm(phone)
        val balancePath = normalizePath(prefs.getSabafonBalancePath())
        val balanceUrl = if (balancePath.contains("?")) {
            "$SABAFON_BASE$balancePath"
        } else {
            "$SABAFON_BASE$balancePath?GSM=$gsm"
        }

        var balanceResp = executeRequest(
            method = "GET",
            url = balanceUrl,
            bearerToken = token
        )

        if (balanceResp.code == 429) {
            return InquiryFetchOutcome.Error(handleSabafonRateLimit(balanceResp.retryAfterSeconds))
        }

        // Only if the server explicitly says 405 Method Not Allowed, send a single POST to the same exact endpoint
        if (balanceResp.code == 405) {
            ensureSabafonPacing()
            balanceResp = executeRequest(
                method = "POST",
                url = "$SABAFON_BASE$balancePath",
                jsonBody = JSONObject().put("GSM", gsm).toString(),
                bearerToken = token
            )
            if (balanceResp.code == 429) {
                return InquiryFetchOutcome.Error(handleSabafonRateLimit(balanceResp.retryAfterSeconds))
            }
        }

        if (balanceResp.networkError != null) {
            return InquiryFetchOutcome.Error("تعذر جلب بيانات رصيد سبأفون، يرجى التحقق من اتصال الإنترنت")
        }

        if (balanceResp.code == 401 || balanceResp.code == 403) {
            prefs.clearToken(phone)
            return InquiryFetchOutcome.NeedsReauth
        }

        if (balanceResp.code == 404) {
            return InquiryFetchOutcome.Error(
                "تم التحقق بنجاح لكن مسار الرصيد غير مطابق (404) — عدّل مسار الرصيد من قائمة المطور"
            )
        }

        if (balanceResp.code !in 200..299 || balanceResp.body.isBlank()) {
            return InquiryFetchOutcome.Error(
                sanitizeServerError(balanceResp.body, "تعذر قراءة تفاصيل رصيد سبأفون حالياً")
            )
        }

        val baseResult = parseBalanceJson(balanceResp.body, phone, CarrierType.SABAFON, false)
        if (baseResult.offers.isNotEmpty()) {
            return InquiryFetchOutcome.Success(baseResult)
        }

        // Single gentle request for active packages (only if not already included in balance response)
        val packages = fetchSabafonActivePackagesSingle(gsm, token)
        return InquiryFetchOutcome.Success(baseResult.copy(offers = packages))
    }

    private fun fetchSabafonActivePackagesSingle(gsm: String, token: String): List<ActivePackage> {
        if (System.currentTimeMillis() < sabafonRateLimitedUntilMillis) return emptyList()
        ensureSabafonPacing()

        val pkgPath = normalizePath(prefs.getSabafonPackagesPath())
        val pkgUrl = if (pkgPath.contains("?")) {
            "$SABAFON_BASE$pkgPath"
        } else {
            "$SABAFON_BASE$pkgPath?GSM=$gsm"
        }

        var resp = executeRequest(
            method = "GET",
            url = pkgUrl,
            bearerToken = token
        )

        if (resp.code == 429) {
            handleSabafonRateLimit(resp.retryAfterSeconds)
            return emptyList()
        }

        if (resp.code == 405) {
            ensureSabafonPacing()
            resp = executeRequest(
                method = "POST",
                url = "$SABAFON_BASE$pkgPath",
                jsonBody = JSONObject().put("GSM", gsm).toString(),
                bearerToken = token
            )
            if (resp.code == 429) {
                handleSabafonRateLimit(resp.retryAfterSeconds)
                return emptyList()
            }
        }

        if (resp.code in 200..299 && resp.body.isNotBlank()) {
            return extractPackagesFromRawBody(resp.body)
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

    private fun buildRateLimitArabicMessage(retryAfterSeconds: Int?): String {
        return if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            "تم تجاوز الحد المسموح للطلبات مؤقتاً، يرجى الانتظار $retryAfterSeconds ثانية ثم المحاولة مجدداً"
        } else {
            "تم تجاوز الحد المسموح للطلبات مؤقتاً، يرجى الانتظار نصف دقيقة ثم المحاولة مجدداً"
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
        if (lower.contains("rate limit")) {
            return buildRateLimitArabicMessage(30)
        }
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
        val retryAfterSeconds: Int? = null,
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
                .header("Accept-Language", "ar")
                .header("User-Agent", "YMBalance/2.0 (Android)")

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
                val retryAfter = response.header("Retry-After")?.trim()?.toIntOrNull()
                val shortPath = url.substringAfter("://").substringAfter("/", "")
                recordTrace("$method /$shortPath -> HTTP ${response.code} (${respBody.take(120)})")
                RawHttpResponse(
                    code = response.code,
                    body = respBody,
                    retryAfterSeconds = retryAfter
                )
            }
        } catch (t: Throwable) {
            val shortPath = url.substringAfter("://").substringAfter("/", "")
            recordTrace("$method /$shortPath -> ERR ${t.javaClass.simpleName}: ${t.localizedMessage}")
            RawHttpResponse(networkError = t)
        }
    }

    companion object {
        const val YEMEN_MOBILE_BASE = "https://82.114.168.238:6321"
        const val SABAFON_BASE = "https://ana2.sabafon.net.ye:20005"
        private const val SABAFON_MIN_PACING_MS = 1600L
    }
}
