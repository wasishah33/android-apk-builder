package com.example.sampleapp

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Supabase REST/Auth client. Retries transient failures, not rejected credentials. */
class SupabaseClient(private val projectUrl: String, private val publishableKey: String) {
    data class Session(val accessToken: String, val refreshToken: String, val userId: String, val email: String)
    data class Response(val status: Int, val body: String) {
        fun json(): JSONObject = if (body.isBlank()) JSONObject() else JSONObject(body)
    }
    class ApiException(val status: Int, message: String) : Exception(message)
    private val baseUrl = projectUrl.trimEnd('/')

    fun isConfigured() = baseUrl.startsWith("https://") && publishableKey.isNotBlank()

    fun signUp(email: String, password: String, username: String) = request(
        "POST", "/auth/v1/signup?redirect_to=" +
            java.net.URLEncoder.encode("myinvest://confirm-email", "UTF-8"),
        JSONObject().put("email", email).put("password", password)
            .put("data", JSONObject().put("username", username).put("full_name", username)).toString()
    )

    fun signIn(email: String, password: String): Session {
        val response = request("POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("email", email).put("password", password).toString())
        val obj = response.json()
        val user = obj.optJSONObject("user")
        val access = obj.optString("access_token")
        if (access.isBlank() || user == null) {
            throw ApiException(response.status, "Sign-in did not return a session. Check email confirmation and try again.")
        }
        return Session(access, obj.optString("refresh_token"), user.optString("id"), user.optString("email", email))
    }

    fun refresh(refreshToken: String): Session {
        val response = request("POST", "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", refreshToken).toString())
        val obj = response.json()
        val user = obj.optJSONObject("user")
        val access = obj.optString("access_token")
        if (access.isBlank() || user == null) throw ApiException(response.status, "Session expired. Please sign in again.")
        return Session(access, obj.optString("refresh_token", refreshToken), user.optString("id"), user.optString("email"))
    }

    fun signOut(accessToken: String) { request("POST", "/auth/v1/logout", null, accessToken) }

    fun requestPasswordReset(email: String) {
        request("POST", "/auth/v1/recover?redirect_to=" +
            java.net.URLEncoder.encode("myinvest://reset-password", "UTF-8"),
            JSONObject().put("email", email.trim()).toString())
    }

    fun updatePassword(accessToken: String, newPassword: String) {
        request("PUT", "/auth/v1/user", JSONObject().put("password", newPassword).toString(), accessToken)
    }

    fun fetchProfile(accessToken: String, userId: String): JSONObject {
        val response = request("GET",
            "/rest/v1/profiles?select=id,email,username,full_name,avatar_image_path,role,account_status&id=eq.$userId", null, accessToken)
        val rows = JSONArray(response.body)
        if (rows.length() == 0) throw ApiException(404, "Your cloud profile is missing. Check that the database schema was installed.")
        return rows.getJSONObject(0)
    }

    fun fetchWallet(accessToken: String, userId: String): JSONObject {
        val response = request("GET",
            "/rest/v1/wallet_accounts?select=deposited_amount,capital_amount,profit_amount,withdrawals_amount&user_id=eq.$userId",
            null, accessToken)
        val rows = JSONArray(response.body)
        return if (rows.length() > 0) rows.getJSONObject(0) else JSONObject()
    }

    fun fetchProfileDetails(accessToken: String, userId: String): JSONObject {
        val rows = JSONArray(request("GET",
            "/rest/v1/profiles?select=id,email,username,full_name,country,date_of_birth,address,phone,avatar_image_path,role,account_status&id=eq.$userId",
            null, accessToken).body)
        if (rows.length() == 0) throw ApiException(404, "Cloud profile not found.")
        return rows.getJSONObject(0)
    }

    fun updateProfile(accessToken: String, fullName: String, country: String, dateOfBirth: String, address: String, phone: String) {
        val userId = request("GET", "/auth/v1/user", null, accessToken).json().optString("id")
        request("PATCH", "/rest/v1/profiles?id=eq.$userId",
            JSONObject().put("full_name", fullName).put("country", country).put("date_of_birth", dateOfBirth)
                .put("address", address).put("phone", phone).toString(), accessToken)
    }

    fun updateAvatarPath(accessToken: String, path: String) {
        val userId = request("GET", "/auth/v1/user", null, accessToken).json().optString("id")
        request("PATCH", "/rest/v1/profiles?id=eq.$userId",
            JSONObject().put("avatar_image_path", path).toString(), accessToken)
    }

    fun fetchLatestKycStatus(accessToken: String, userId: String): String {
        val rows = JSONArray(request("GET",
            "/rest/v1/kyc_submissions?select=status&user_id=eq.$userId&order=created_at.desc&limit=1",
            null, accessToken).body)
        return if (rows.length() == 0) "not_submitted" else rows.getJSONObject(0).optString("status", "pending")
    }

    fun fetchLatestKycFacePath(accessToken: String, userId: String): String {
        val rows = JSONArray(request("GET",
            "/rest/v1/kyc_submissions?select=face_image_path&user_id=eq.$userId&order=created_at.desc&limit=1",
            null, accessToken).body)
        return if (rows.length() == 0) "" else rows.getJSONObject(0).optString("face_image_path", "")
    }

    fun uploadKycImage(accessToken: String, userId: String, kind: String, bytes: ByteArray, contentType: String): String {
        if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) throw ApiException(400, "Choose an image smaller than 8 MB.")
        val path = userId + "/" + kind + "-" + System.currentTimeMillis() + ".jpg"
        val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        var lastError: Exception? = null
        for (attempt in 0..2) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(baseUrl + "/storage/v1/object/kyc-documents/" + encodedPath).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 10000; readTimeout = 15000; doOutput = true
                    setRequestProperty("apikey", publishableKey)
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", contentType)
                    setRequestProperty("x-upsert", "false")
                }
                connection.outputStream.use { it.write(bytes) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (status in 200..299) return path
                if ((status == 408 || status == 429 || status >= 500) && attempt < 2) {
                    Thread.sleep(500L * (1L shl attempt)); continue
                }
                val message = try { JSONObject(body).optString("message", "KYC image upload failed (HTTP $status).") }
                    catch (_: Exception) { "KYC image upload failed (HTTP $status)." }
                throw ApiException(status, message)
            } catch (e: ApiException) { throw e }
            catch (e: Exception) {
                lastError = e
                if (attempt < 2) Thread.sleep(500L * (1L shl attempt))
            } finally { connection?.disconnect() }
        }
        throw IOException("KYC image upload failed after 3 attempts.", lastError)
    }

    fun submitKyc(accessToken: String, userId: String, documentType: String, idPath: String, facePath: String) {
        request("POST", "/rest/v1/kyc_submissions",
            JSONObject().put("user_id", userId).put("document_type", documentType)
                .put("id_image_path", idPath).put("face_image_path", facePath).put("status", "pending").toString(),
            accessToken)
    }

    fun fetchPlans(accessToken: String): JSONArray =
        JSONArray(request("GET", "/rest/v1/investment_plans?select=id,slug,name,icon,minimum_amount,fixed_profit_rate,is_active&is_active=eq.true&order=minimum_amount.asc", null, accessToken).body)

    fun fetchInvestments(accessToken: String): JSONArray =
        JSONArray(request("GET", "/rest/v1/investments?select=id,plan_id,principal_amount,fixed_profit_amount,fixed_profit_rate,status,created_at,updated_at,investment_plans(name,slug,icon)&order=created_at.desc&limit=100", null, accessToken).body)

    fun fetchAdminPlans(accessToken: String): JSONArray =
        JSONArray(request("GET", "/rest/v1/investment_plans?select=id,slug,name,icon,minimum_amount,fixed_profit_rate,is_active&order=minimum_amount.asc", null, accessToken).body)

    fun adminUpdatePlan(accessToken: String, planId: String, name: String, minimum: Double, rate: Double, active: Boolean) {
        request("POST", "/rest/v1/rpc/admin_update_investment_plan",
            JSONObject().put("p_plan_id", planId).put("p_name", name.trim())
                .put("p_minimum_amount", minimum).put("p_fixed_profit_rate", rate)
                .put("p_is_active", active).toString(), accessToken)
    }

    fun requestInvestment(accessToken: String, planSlug: String, amount: Double) {
        request("POST", "/rest/v1/rpc/request_investment",
            JSONObject().put("p_plan_slug", planSlug).put("p_amount", amount).toString(), accessToken)
    }

    fun requestWithdrawal(accessToken: String, amount: Double) {
        request("POST", "/rest/v1/rpc/request_withdrawal",
            JSONObject().put("p_amount", amount).toString(), accessToken)
    }

    fun fetchTransactions(accessToken: String, userId: String): JSONArray =
        JSONArray(request("GET",
            "/rest/v1/transactions?select=id,transaction_type,amount,status,description,created_at&user_id=eq.$userId&order=created_at.desc&limit=100",
            null, accessToken).body)

    fun fetchPendingWithdrawals(accessToken: String): JSONArray =
        JSONArray(request("GET", "/rest/v1/transactions?select=id,user_id,amount,description,status,created_at&transaction_type=eq.withdrawal_request&status=eq.pending&order=created_at.asc&limit=100", null, accessToken).body)

    fun adminReviewWithdrawal(accessToken: String, transactionId: String, decision: String, reason: String) {
        request("POST", "/rest/v1/rpc/admin_review_withdrawal",
            JSONObject().put("p_transaction_id", transactionId).put("p_decision", decision)
                .put("p_reason", reason).toString(), accessToken)
    }

    fun fetchPendingKyc(accessToken: String): JSONArray =
        JSONArray(request("GET", "/rest/v1/kyc_submissions?select=id,user_id,document_type,id_image_path,face_image_path,status,created_at&status=eq.pending&order=created_at.asc&limit=100", null, accessToken).body)

    fun reviewKyc(accessToken: String, submissionId: String, status: String, reason: String) {
        val userId = request("GET", "/auth/v1/user", null, accessToken).json().optString("id")
        request("PATCH", "/rest/v1/kyc_submissions?id=eq.$submissionId",
            JSONObject().put("status", status).put("review_reason", reason)
                .put("reviewed_by", userId).put("reviewed_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())).toString(),
            accessToken)
    }

    fun signedKycUrl(accessToken: String, path: String): String {
        val encoded = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val response = request("POST", "/storage/v1/object/sign/kyc-documents/" + encoded,
            JSONObject().put("expiresIn", 300).toString(), accessToken)
        val signed = response.json().optString("signedURL")
        if (signed.isBlank()) throw ApiException(response.status, "Could not create a temporary document link.")
        return if (signed.startsWith("http")) signed else baseUrl + "/storage/v1" + (if (signed.startsWith("/")) signed else "/$signed")
    }

    fun fetchPendingInvestments(accessToken: String): JSONArray =
        JSONArray(request("GET", "/rest/v1/investments?select=id,user_id,principal_amount,fixed_profit_amount,status,created_at&status=eq.pending&order=created_at.asc&limit=100", null, accessToken).body)

    fun adminReviewInvestment(accessToken: String, investmentId: String, decision: String, reason: String) {
        request("POST", "/rest/v1/rpc/admin_review_investment",
            JSONObject().put("p_investment_id", investmentId).put("p_decision", decision)
                .put("p_reason", reason).toString(), accessToken)
    }

    fun searchUsers(accessToken: String, email: String): JSONArray {
        val filter = if (email.isBlank()) "" else "&email=ilike.*" + java.net.URLEncoder.encode(email.trim(), "UTF-8") + "*"
        return JSONArray(request("GET",
            "/rest/v1/profiles?select=id,email,full_name,role,account_status,created_at&order=created_at.desc&limit=100" + filter,
            null, accessToken).body)
    }

    fun adminAdjustWallet(accessToken: String, userId: String, field: String, amount: Double, reason: String) {
        request("POST", "/rest/v1/rpc/admin_adjust_wallet",
            JSONObject().put("p_user_id", userId).put("p_field", field)
                .put("p_new_amount", amount).put("p_reason", reason).toString(), accessToken)
    }

    fun adminSetProfileStatus(accessToken: String, userId: String, status: String, reason: String) {
        request("POST", "/rest/v1/rpc/admin_set_profile_status",
            JSONObject().put("p_user_id", userId).put("p_status", status).put("p_reason", reason).toString(), accessToken)
    }

    fun notifyAccountStatus(accessToken: String, userId: String, status: String, reason: String) {
        request("POST", "/functions/v1/admin-notify",
            JSONObject().put("user_id", userId).put("status", status).put("reason", reason).toString(), accessToken)
    }

    private fun request(method: String, path: String, body: String? = null, accessToken: String? = null): Response {
        if (!isConfigured()) throw ApiException(0, "Cloud connection is not configured. Check GitHub Actions secrets.")
        var lastError: Exception? = null
        for (attempt in 0..2) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 10000
                    readTimeout = 12000
                    setRequestProperty("apikey", publishableKey)
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", "MY-INVEST-Android")
                    setRequestProperty("Authorization", "Bearer " + (accessToken ?: publishableKey))
                    if (body != null) doOutput = true
                }
                if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (status in 200..299) return Response(status, text)
                if ((status == 408 || status == 429 || status >= 500) && attempt < 2) {
                    Thread.sleep(500L * (1L shl attempt))
                    continue
                }
                val message = try {
                    val error = JSONObject(text)
                    error.optString("msg").ifBlank { error.optString("message").ifBlank { "Cloud request failed (HTTP $status)." } }
                } catch (_: Exception) { "Cloud request failed (HTTP $status)." }
                throw ApiException(status, message)
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) {
                    try { Thread.sleep(500L * (1L shl attempt)) } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IOException("Connection retry interrupted.")
                    }
                }
            } finally { connection?.disconnect() }
        }
        throw IOException("Unable to reach MY INVEST cloud service after 3 attempts. Check your internet connection and try again.", lastError)
    }
}
