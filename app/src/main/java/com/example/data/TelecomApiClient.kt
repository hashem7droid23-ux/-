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

    private val traceHistory = ArrayDeque<String>(24)

    private fun recordTrace(line: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        synchronized(traceHistory) {
            if (traceHistory.size >= 20) {
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
    // Smart Paced Discovery (2.4s spacing, stops immediately on 2xx/400/401/429)
    // Automatically saves discovered working endpoints to AppPreferences!
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
        val cooldownSec = (retryAfterSeconds ?: 25).coerceIn(10, 90)
        sabafonRateLimitedUntilMillis = System.currentTimeMillis() + (cooldownSec * 1000L)
        return buildRateLimitArabicMessage(cooldownSec)
    }

    private fun startSabafonInquiry(phone: String): InquiryStartOutcome {
        checkSabafonCooldown()?.let { return InquiryStartOutcome.Error(it) }

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

        val gsm = formatSabafonGsm(phone)
        val cleanOtp = otpCode.trim()

        // Distinct non-colliding PascalCase keys compatible with ASP.NET Core System.Text.Json DTOs
        val verifyPayload = JSONObject().apply {
            put("GSM", gsm)
            put("OTP", cleanOtp)
            put("Code", cleanOtp)
            put("OtpCode", cleanOtp)
            put("Pin", cleanOtp)
            put("Password", cleanOtp)
            put("VerificationCode", cleanOtp)
        }.toString()

        val configuredVerify = normalizePath(prefs.getSabafonVerifyPath())

        // High-probability ASP.NET Core AuthenticateController actions paired with SendOtp
        // Spaced by 2.4s each and capped at 4 attempts per tap to stay safely under Kong's rate limit
        val candidateVerifyPaths = linkedSetOf(
            configuredVerify,
            "/ana-sabafon-api-auth/Authenticate/Login",
            "/ana-sabafon-api-auth/Authenticate/Verify",
            "/ana-sabafon-api-auth/Authenticate/CheckOtp",
            "/ana-sabafon-api-auth/Authenticate/LoginWithOtp",
            "/ana-sabafon-api-auth/Authenticate/LoginByOtp",
            "/ana-sabafon-api-auth/Authenticate/ConfirmOtp",
            "/ana-sabafon-api-auth/Authenticate/ValidateOtp",
            "/ana-sabafon-api-auth/Authenticate/Token",
            "/ana-sabafon-api-auth/Authenticate/Authenticate"
        ).take(4)

        var lastResp: RawHttpResponse? = null
        var matchedPath: String? = null
        var successBody: String? = null

        for (path in candidateVerifyPaths) {
            ensureSabafonPacing()
            val resp = executeRequest(
                method = "POST",
                url = "$SABAFON_BASE$path",
                jsonBody = verifyPayload
            )
            lastResp = resp

            if (resp.networkError != null) {
                return InquiryFetchOutcome.Error("تعذر الاتصال بخادم سبأفون أثناء التحقق من الرمز")
            }

            if (resp.code == 429) {
                return InquiryFetchOutcome.Error(handleSabafonRateLimit(resp.retryAfterSeconds))
            }

            if (resp.code in 200..299) {
                matchedPath = path
                successBody = resp.body
                prefs.setSabafonVerifyPath(path)
                break
            }

            // 400 / 401 / 403 / 422 proves THIS route exists on ASP.NET Core!
            if (resp.code in listOf(400, 401, 403, 422)) {
                prefs.setSabafonVerifyPath(path)
                // If 400 mentions a missing required field (like PhoneNumber or Username or DeviceId), try once more with extended payload
                if (resp.code == 400 && resp.body.isNotBlank()) {
                    val retryBody = buildAdaptiveRetryPayload(gsm, phone, cleanOtp, resp.body)
                    if (retryBody != null) {
                        ensureSabafonPacing()
                        val retryResp = executeRequest(
                            method = "POST",
                            url = "$SABAFON_BASE$path",
                            jsonBody = retryBody
                        )
                        if (retryResp.code in 200..299) {
                            matchedPath = path
                            successBody = retryResp.body
                            break
                        }
                        if (retryResp.code == 429) {
                            return InquiryFetchOutcome.Error(handleSabafonRateLimit(retryResp.retryAfterSeconds))
                        }
                    }
                }
                return InquiryFetchOutcome.Error(
                    sanitizeServerError(resp.body, "رمز التحقق غير صحيح أو منتهي الصلاحية")
                )
            }

            // 404 or 405 -> continue gently to next candidate path after pacing
        }

        if (successBody == null || matchedPath == null) {
            val statusCode = lastResp?.code ?: -1
            return InquiryFetchOutcome.Error(
                "تعذر العثور على مسار التحقق على خادم سبأفون ($statusCode) — افتح قائمة المطور (5 نقرات على الإصدار) واضغط «اكتشاف مسارات سبأفون» أو انسخ سجل التشخيص"
            )
        }

        val token = extractTokenFromBody(successBody)
        if (!token.isNullOrBlank()) {
            prefs.saveToken(phone, token)
            if (containsBalanceFields(successBody)) {
                return InquiryFetchOutcome.Success(
                    parseBalanceJson(successBody, phone, CarrierType.SABAFON, false)
                )
            }
            return fetchSabafonBalanceAndPackages(phone, token)
        }

        if (containsBalanceFields(successBody)) {
            return InquiryFetchOutcome.Success(
                parseBalanceJson(successBody, phone, CarrierType.SABAFON, false)
            )
        }

        return InquiryFetchOutcome.Error("تم استلام رد من سبأفون ($matchedPath) بدون رمز جلسة — راجع سجل التشخيص في قائمة المطور")
    }

    private fun buildAdaptiveRetryPayload(
        gsm: String,
        rawPhone: String,
        otp: String,
        errorBody: String
    ): String? {
        val lower = errorBody.lowercase()
        val obj = JSONObject().apply {
            put("GSM", gsm)
            put("OTP", otp)
            put("Code", otp)
            if (lower.contains("phonenumber") || lower.contains("phone")) put("PhoneNumber", gsm)
            if (lower.contains("msisdn")) put("Msisdn", gsm)
            if (lower.contains("username")) put("UserName", gsm)
            if (lower.contains("mobile")) put("MobileNumber", rawPhone)
            if (lower.contains("device")) put("DeviceId", "android-device-01")
            if (lower.contains("platform") || lower.contains("os")) put("Platform", "Android")
            if (lower.contains("lang")) put("Language", "ar")
        }
        return if (obj.length() > 3) obj.toString() else null
    }

    /**
     * Developer-triggered safe discovery tool (paced 2.4s apart) to probe Swagger/OpenAPI
     * and common Ana Sabafon ASP.NET Core Authenticate routes without tripping rate limits.
     */
    fun discoverSabafonEndpoints(onProgress: (String) -> Unit = {}): String {
        recordTrace("=== بدء اكتشاف مسارات سبأفون ===")
        onProgress(lastDiagnosticLog)

        val probeTargets = listOf(
            "GET" to "/ana-sabafon-api-auth/swagger/v1/swagger.json",
            "POST" to "/ana-sabafon-api-auth/Authenticate/Login",
            "POST" to "/ana-sabafon-api-auth/Authenticate/Verify",
            "POST" to "/ana-sabafon-api-auth/Authenticate/CheckOtp",
            "POST" to "/ana-sabafon-api-auth/Authenticate/ConfirmOtp",
            "POST" to "/ana-sabafon-api-auth/Authenticate/ValidateOtp",
            "POST" to "/ana-sabafon-api-auth/Authenticate/OtpLogin",
            "POST" to "/ana-sabafon-api-auth/Authenticate/LoginWithOtp",
            "POST" to "/ana-sabafon-api-auth/Authenticate/VerifyCode",
            "POST" to "/ana-sabafon-api-auth/Authenticate/Token",
            "POST" to "/ana-sabafon-api-auth/Authenticate/Authenticate",
            "POST" to "/ana-sabafon-api-auth/Authenticate/VerifyOtp/967710000000/000000"
        )

        val dummyBody = JSONObject().apply {
            put("GSM", "967710000000")
            put("OTP", "000000")
            put("Code", "000000")
        }.toString()

        var discoveredVerify: String? = null

        for ((method, path) in probeTargets) {
            if (System.currentTimeMillis() < sabafonRateLimitedUntilMillis) {
                recordTrace("توقف الفحص مؤقتاً بسبب حد الطلبات (429)")
                onProgress(lastDiagnosticLog)
                break
            }
            ensureSabafonPacing()
            val resp = executeRequest(
                method = method,
                url = "$SABAFON_BASE$path",
                jsonBody = if (method == "POST") dummyBody else null
            )
            onProgress(lastDiagnosticLog)

            if (resp.code == 429) {
                handleSabafonRateLimit(resp.retryAfterSeconds)
                recordTrace("تم إيقاف الفحص مؤقتاً لتفادي الحظر (429)")
                onProgress(lastDiagnosticLog)
                break
            }

            if (method == "GET" && resp.code in 200..299 && resp.body.contains("\"paths\"")) {
                try {
                    val swagger = JSONObject(resp.body)
                    val pathsObj = swagger.optJSONObject("paths")
                    val keys = pathsObj?.keys()
                    val foundPaths = mutableListOf<String>()
                    while (keys != null && keys.hasNext()) {
                        foundPaths.add(keys.next())
                    }
                    recordTrace("Swagger Paths: ${foundPaths.joinToString(", ")}")
                    val verifyCandidate = foundPaths.firstOrNull { p ->
                        val l = p.lowercase()
                        (l.contains("verify") || l.contains("login") || l.contains("check") || l.contains("confirm") || l.contains("token")) &&
                            !l.contains("sendotp")
                    }
                    if (!verifyCandidate.isNullOrBlank()) {
                        val full = if (verifyCandidate.startsWith("/ana-sabafon")) {
                            verifyCandidate
                        } else {
                            "/ana-sabafon-api-auth$verifyCandidate"
                        }
                        prefs.setSabafonVerifyPath(full)
                        discoveredVerify = full
                        recordTrace("تم ضبط مسار التحقق تلقائياً: $full")
                    }
                    onProgress(lastDiagnosticLog)
                } catch (_: Throwable) {
                }
                break
            }

            if (method == "POST" && resp.code in listOf(200, 201, 400, 401, 403, 405, 422)) {
                prefs.setSabafonVerifyPath(path)
                discoveredVerify = path
                recordTrace("تم اكتشاف مسار تحقق فعّال: $path (HTTP ${resp.code})")
                onProgress(lastDiagnosticLog)
                break
            }
        }

        recordTrace("=== انتهى الاكتشاف ===")
        onProgress(lastDiagnosticLog)
        return discoveredVerify ?: ""
    }

    private fun fetchSabafonBalanceAndPackages(phone: String, token: String): InquiryFetchOutcome {
        checkSabafonCooldown()?.let { return InquiryFetchOutcome.Error(it) }

        val gsm = formatSabafonGsm(phone)
        val configuredBalance = normalizePath(prefs.getSabafonBalancePath())
        val balanceCandidates = linkedSetOf(
            configuredBalance,
            "/ana-sabafon-api-subscriber/Subscriber/GetBalance",
            "/ana-sabafon-api-balance/Balance/GetBalance",
            "/ana-sabafon-api-inquiry/Inquiry/GetBalance",
            "/ana-sabafon-api/Subscriber/GetBalance"
        ).take(3)

        var balanceResp: RawHttpResponse? = null
        var resolvedPath: String? = null

        for (path in balanceCandidates) {
            ensureSabafonPacing()
            val url = if (path.contains("?")) "$SABAFON_BASE$path" else "$SABAFON_BASE$path?GSM=$gsm"
            var resp = executeRequest(method = "GET", url = url, bearerToken = token)

            if (resp.code == 429) {
                return InquiryFetchOutcome.Error(handleSabafonRateLimit(resp.retryAfterSeconds))
            }

            if (resp.code == 405) {
                ensureSabafonPacing()
                resp = executeRequest(
                    method = "POST",
                    url = "$SABAFON_BASE$path",
                    jsonBody = JSONObject().put("GSM", gsm).toString(),
                    bearerToken = token
                )
                if (resp.code == 429) {
                    return InquiryFetchOutcome.Error(handleSabafonRateLimit(resp.retryAfterSeconds))
                }
            }

            balanceResp = resp

            if (resp.networkError != null) {
                return InquiryFetchOutcome.Error("تعذر جلب بيانات رصيد سبأفون، يرجى التحقق من اتصال الإنترنت")
            }

            if (resp.code == 401 || resp.code == 403) {
                prefs.clearToken(phone)
                return InquiryFetchOutcome.NeedsReauth
            }

            if (resp.code in 200..299 && resp.body.isNotBlank()) {
                resolvedPath = path
                prefs.setSabafonBalancePath(path)
                break
            }

            if (resp.code != 404) {
                break
            }
        }

        val finalResp = balanceResp
        if (resolvedPath == null || finalResp == null || finalResp.code !in 200..299 || finalResp.body.isBlank()) {
            val code = finalResp?.code ?: -1
            if (code == 404) {
                return InquiryFetchOutcome.Error(
                    "تم التحقق بنجاح لكن مسار الرصيد غير مطابق (404) — عدّل مسار الرصيد من قائمة المطور"
                )
            }
            return InquiryFetchOutcome.Error(
                sanitizeServerError(finalResp?.body.orEmpty(), "تعذر قراءة تفاصيل رصيد سبأفون حالياً")
            )
        }

        val baseResult = parseBalanceJson(finalResp.body, phone, CarrierType.SABAFON, false)
        if (baseResult.offers.isNotEmpty()) {
            return InquiryFetchOutcome.Success(baseResult)
        }

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
                recordTrace("$method /$shortPath -> HTTP ${response.code} (${respBody.take(160)})")
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
        private const val SABAFON_MIN_PACING_MS = 2400L
    }
}
