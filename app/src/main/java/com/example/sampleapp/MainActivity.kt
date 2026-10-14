package com.example.sampleapp

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.view.View
import android.widget.*
import android.app.ProgressDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val p by lazy { getSharedPreferences("myinvest_local", MODE_PRIVATE) }
    private val cloud by lazy { SupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) }
    @Volatile private var cloudAccessToken: String? = null
    @Volatile private var cloudRefreshToken: String? = null
    @Volatile private var cloudUserId: String? = null
    @Volatile private var cloudRole: String = "investor"
    private var selectedIdImageUri: Uri? = null
    private var selectedFaceImageUri: Uri? = null
    private var selectedAvatarImageUri: Uri? = null
    @Volatile private var kycApproved: Boolean = false
    @Volatile private var latestKycStatus: String = "not_submitted"
    private val ink=Color.rgb(26,39,55); private val muted=Color.rgb(111,124,139)
    private val green=Color.rgb(15,130,88); private val pale=Color.rgb(235,246,240); private val bg=Color.rgb(247,249,248)
    private var marketValues=floatArrayOf(); private var marketStatus="Loading market data…"; private var marketChange=""
    private val navigationStack = java.util.ArrayDeque<View>()
    private var activePage: View? = null
    private var lastScrollView: ScrollView? = null
    private var loadingDialog: android.app.Dialog? = null
    private var loadingDialogMessage: TextView? = null
    private var loadingGeneration: Int = 0
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        // The app uses its own branded navigation header. Hide the system action bar
        // so the duplicate app-title strip never appears above the logo/header.
        supportActionBar?.hide()
        window.statusBarColor=bg; window.navigationBarColor=Color.WHITE
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        // Require credentials whenever the app is launched into a fresh Activity.
        // Do not restore a saved refresh token on startup; that caused a race where
        // the login screen briefly appeared and then opened the dashboard automatically.
        p.edit()
            .remove("cloud_access_token")
            .remove("cloud_refresh_token")
            .remove("cloud_user_id")
            .putBoolean("logged", false)
            .apply()
        cloudAccessToken = null
        cloudRefreshToken = null
        cloudUserId = null
        auth(false)
        val incoming=intent?.data
        if(incoming?.scheme=="myinvest" && incoming.host=="confirm-email") {
            val params=(incoming.query.orEmpty()+"&"+incoming.fragment.orEmpty()).split("&").mapNotNull { part ->
                val pieces=part.split("=", limit=2)
                if(pieces.size==2) pieces[0] to Uri.decode(pieces[1].replace("+","%2B")) else null
            }.toMap()
            val error=params["error_description"] ?: params["error"]
            if(!error.isNullOrBlank()) toast("Email confirmation failed: "+error.replace("+"," "))
            else toast("Email confirmed successfully. Sign in with your email and password to continue.")
        }
        if(incoming?.scheme=="myinvest" && incoming.host=="reset-password") {
            val fragment=incoming.fragment.orEmpty().split("&").mapNotNull { part ->
                val pieces=part.split("=", limit=2)
                if(pieces.size==2) pieces[0] to Uri.decode(pieces[1]) else null
            }.toMap()
            val resetToken=fragment["access_token"]
            if(fragment["type"]=="recovery" && !resetToken.isNullOrBlank()) { resetPasswordScreen(resetToken); return }
        }
    }
    override fun onBackPressed() {
        if (!navigationStack.isEmpty()) {
            val previous = navigationStack.removeLast()
            activePage = previous
            setContentView(previous)
        } else super.onBackPressed()
    }

    private fun showPage(view: View, root: Boolean = false) {
        if (root) navigationStack.clear()
        else activePage?.let { previous -> if (previous !== view) navigationStack.addLast(previous) }
        activePage = view
        setContentView(view)
        // Show the branded loading indicator on every page navigation, including
        // quick local pages, and let longer page-specific operations keep their own message.
        showLoading("Loading page…")
        android.os.Handler(mainLooper).postDelayed({
            if (loadingDialog?.isShowing == true && loadingDialogMessage?.text == "Loading page…") {
                hideLoading()
            }
        }, 450)
    }

    private fun showLoading(message: String) {
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            loadingGeneration++
            if (loadingDialog?.isShowing == true) {
                loadingDialogMessage?.text = message
                return@runOnUiThread
            }

            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(22), dp(20), dp(24), dp(20))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.rgb(12, 27, 45))
                    cornerRadius = dp(22).toFloat()
                    setStroke(dp(1), Color.rgb(38, 91, 91))
                }
                elevation = dp(12).toFloat()
            }
            val spinner = ProgressBar(this).apply {
                isIndeterminate = true
                indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.rgb(39, 211, 145))
            }
            panel.addView(spinner, LinearLayout.LayoutParams(dp(36), dp(36)))
            val messageView = txt(message, 14, Color.WHITE, true).apply {
                maxLines = 3
                setLineSpacing(dp(2).toFloat(), 1f)
            }
            loadingDialogMessage = messageView
            panel.addView(messageView, LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = dp(16)
            })

            loadingDialog = android.app.Dialog(this).apply {
                setContentView(panel)
                setCancelable(false)
                window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                window?.setLayout((resources.displayMetrics.widthPixels - dp(48)), -2)
                show()
                window?.setLayout((resources.displayMetrics.widthPixels - dp(48)), -2)
            }
        }
    }

    private fun hideLoading() {
        val generationAtRequest = loadingGeneration
        runOnUiThread {
            if (generationAtRequest == loadingGeneration) {
                loadingDialog?.dismiss()
                loadingDialog = null
                loadingDialogMessage = null
            }
        }
    }

    private fun saveSession(session:SupabaseClient.Session) {
        cloudAccessToken=session.accessToken; cloudRefreshToken=session.refreshToken; cloudUserId=session.userId
        p.edit().putString("cloud_access_token",session.accessToken).putString("cloud_refresh_token",session.refreshToken).putString("cloud_user_id",session.userId).apply()
    }
    private fun loadCloudWalletThenDashboard() {
        showLoading("Loading your secure investment dashboard…")
        val token=cloudAccessToken ?: p.getString("cloud_access_token",null)
        val uid=cloudUserId ?: p.getString("cloud_user_id",null)
        if(token==null || uid==null) { hideLoading(); auth(false); return }
        thread {
            try {
                val wallet=cloud.fetchWallet(token,uid)
                runOnUiThread {
                    p.edit().putFloat("deposited",wallet.optDouble("deposited_amount",0.0).toFloat())
                        .putFloat("capital",wallet.optDouble("capital_amount",0.0).toFloat())
                        .putFloat("profits",wallet.optDouble("profit_amount",0.0).toFloat())
                        .putFloat("withdrawals",wallet.optDouble("withdrawals_amount",0.0).toFloat()).apply()
                    hideLoading()
                    dashboard()
                }
            } catch(e:Exception) { runOnUiThread { hideLoading(); toast("Cloud account data could not be loaded: "+(e.message ?: "network error")); auth(false) } }
        }
    }

    private fun root(title:String,sub:String?=null,showPageTitle:Boolean=true):Pair<LinearLayout,LinearLayout>{
        val shell=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)}
        val header=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16),dp(10),dp(14),dp(10))
            setBackgroundColor(ink)
            elevation=dp(5).toFloat()
        }
        val logoFrame=android.widget.FrameLayout(this).apply{
            background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.rgb(11,20,36));cornerRadius=dp(12).toFloat()}
            layoutParams=LinearLayout.LayoutParams(dp(44),dp(44))
        }
        logoFrame.addView(ImageView(this).apply{
            setImageResource(R.drawable.ic_launcher_foreground)
            scaleType=ImageView.ScaleType.FIT_CENTER
            setPadding(dp(3),dp(3),dp(3),dp(3))
        },android.widget.FrameLayout.LayoutParams(-1,-1))
        header.addView(logoFrame)
        // Keep the single brand lockup beside the icon; the system title bar is hidden.
        header.addView(LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(11),0,0,0)
            addView(txt("EVERCREST CAPITAL",15,Color.WHITE,true))
            addView(txt("INVESTMENT",9,Color.rgb(196,211,222),true,2))
        }, LinearLayout.LayoutParams(0,-2,1f))
        val headerSpacer=View(this)
        header.addView(headerSpacer,LinearLayout.LayoutParams(0,1,0f))
        val nav=TextView(this).apply{
            text="☰"; textSize=25f; gravity=android.view.Gravity.CENTER
            setTextColor(Color.WHITE); contentDescription="Open navigation menu"
            background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.rgb(35,51,70));cornerRadius=dp(13).toFloat()}
            setPadding(dp(10),dp(5),dp(10),dp(5))
            setOnClickListener { anchor ->
                val menu=PopupMenu(this@MainActivity,anchor)
                menu.menu.add("Dashboard").setOnMenuItemClickListener { if(p.getBoolean("logged",false)) dashboard() else auth(false); true }
                menu.menu.add("About Evercrest Capital").setOnMenuItemClickListener { aboutPage(); true }
                if(p.getBoolean("logged",false)) {
                    menu.menu.add("Profile").setOnMenuItemClickListener { profile(); true }
                    menu.menu.add("Sign out").setOnMenuItemClickListener {
                        p.edit().remove("cloud_access_token").remove("cloud_refresh_token").remove("cloud_user_id").putBoolean("logged",false).apply()
                        cloudAccessToken=null; cloudRefreshToken=null; cloudUserId=null; auth(false); true
                    }
                }
                menu.show()
            }
        }
        header.addView(nav,LinearLayout.LayoutParams(dp(44),dp(44)))
        shell.addView(header,LinearLayout.LayoutParams(-1,-2))
        val page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(20),dp(20),dp(28));setBackgroundColor(bg)}
        val scroll=ScrollView(this).apply{isFillViewport=true;addView(page)}
        lastScrollView=scroll
        enablePullToRefresh { refreshPage(title) }
        shell.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        if(showPageTitle) page.addView(txt(title,26,ink,true))
        if(sub!=null)page.addView(txt(sub,14,muted,false,7))
        return shell to page
    }

    private fun aboutPage(){
        val(screen,page)=root("About Evercrest Capital","Explore information about five companies associated with Elon Musk.")
        page.addView(card().apply{
            addView(txt("Our focus",18,ink,true))
            addView(txt("Evercrest Capital states that deposits and investment contributions are allocated through the company’s offline office operations toward acquiring shares or other eligible investment interests associated with Elon Musk’s companies: Tesla, SpaceX / Starlink, xAI, Neuralink, and The Boring Company. This app provides information about the featured companies; the actual investment allocation is handled offline by the company.",14,muted,false,10))
            addView(txt("Important: what your money means",16,ink,true,18))
            addView(txt("The company allocates deposits and investment contributions through its offline office operations toward acquiring shares or other eligible investment interests associated with Tesla, SpaceX / Starlink, xAI, Neuralink, and The Boring Company. Availability and eligibility vary, and some featured companies are private rather than publicly traded.",13,muted,false,8))
            addView(txt("What you can do",16,ink,true,18))
            addView(txt("View account figures in US dollars, explore the five featured companies, open official company or investor-information links, review plan details, check transaction history, manage your profile, and submit requests where enabled. The company allocates deposits and investment contributions through its offline office operations toward eligible investments associated with the featured companies.",14,muted,false,8))
            addView(txt("Important investment information",16,ink,true,18))
            addView(txt("Investment values can rise or fall. Displayed estimates are not guaranteed returns and past performance does not assure future results. Submitting a request in the app does not itself transfer money; requests may require administrator review. Consider the risks and review all applicable terms before making financial decisions.",13,muted,false,8))
            addView(txt("Account security",16,ink,true,18))
            addView(txt("Access to account balances depends on the configured cloud service and account permissions. Keep your sign-in details private and contact the app administrator if you notice activity you do not recognize.",13,muted,false,8))
        },mp(18))
        page.addView(txt("© 2026 Evercrest Capital. All rights reserved.",11,muted,false,22))
        page.addView(txt("Evercrest Capital is an app name and does not, by itself, imply regulatory authorization, deposit protection, or guaranteed investment performance.",11,muted,false,8))
        page.addView(btn("Back to dashboard",false){if(p.getBoolean("logged",false))dashboard() else auth(false)},mp(14))
        showPage(screen)
    }
    private fun auth(register:Boolean){
        val(scroll,page)=root(if(register)"Create account" else "Welcome back",null,false)
        // Modern authentication hero: no duplicate app title above the logo, with a clear
        // hierarchy, premium navy/emerald treatment, and generous mobile spacing.
        page.addView(LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(22),dp(24),dp(22),dp(24))
            background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(9,22,42),Color.rgb(18,52,75),Color.rgb(17,91,78))).apply {
                cornerRadius=dp(26).toFloat(); setStroke(dp(1),Color.rgb(48,103,111))
            }
            addView(txt(if(register)"PRIVATE INVESTOR PORTAL" else "SECURE INVESTOR PORTAL",10,Color.rgb(177,221,212),true))
            addView(txt(if(register)"Create your account" else "Welcome back",29,Color.WHITE,true,dp(12)))
            addView(txt(if(register)"Set up your profile to access your investment dashboard." else "Sign in to view your portfolio, profit and account activity.",14,Color.rgb(211,226,230),false,dp(8)))
            addView(LinearLayout(this@MainActivity).apply {
                orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL
                addView(txt("●  Secure access",10,Color.rgb(219,239,229),true),LinearLayout.LayoutParams(0,-2,1f))
                addView(txt("USD ACCOUNT",9,Color.rgb(235,204,129),true).apply {
                    background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.argb(35,255,255,255));cornerRadius=dp(18).toFloat() }
                    setPadding(dp(10),dp(6),dp(10),dp(6))
                })
            }, mp(18))
        },mp(4))
        val username=input("Username").apply{visibility=if(register)View.VISIBLE else View.GONE}
        val email=input("Email address",InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val pass=input("Password",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        page.addView(txt(if(register)"ACCOUNT DETAILS" else "SIGN IN TO CONTINUE",11,muted,true,dp(24)))
        page.addView(card().apply{
            addView(username);addView(email,mp(12));addView(pass,mp(12))
            addView(btn(if(register)"Create account" else "Sign in",true){
                val n=username.text.toString().trim();val e=email.text.toString().trim().lowercase(Locale.ROOT);val pw=pass.text.toString()
                when {
                    !cloud.isConfigured()->toast("Cloud connection is not configured. Please rebuild after adding the Supabase GitHub secrets.")
                    register&&!n.matches(Regex("[A-Za-z0-9_.-]{3,24}"))->toast("Username must be 3–24 characters using letters, numbers, dot, underscore or hyphen.")
                    !e.contains("@")||!e.contains(".")->toast("Enter a valid email.")
                    pw.length<8->toast("Password must contain at least 8 characters.")
                    else->{
                        showLoading(if(register) "Creating your secure account…" else "Signing you in securely…")
                        thread {
                            try {
                                if(register) {
                                    val response=cloud.signUp(e,pw,n)
                                    val obj=try{response.json()}catch(_:Exception){JSONObject()}
                                    val access=obj.optString("access_token")
                                    val user=obj.optJSONObject("user") ?: obj.optJSONObject("data")?.optJSONObject("user") ?: if(obj.has("id")) obj else null
                                    if(access.isBlank() || user==null) {
                                        runOnUiThread { toast("Registration submitted. Check your email to confirm the account, then sign in. Your profile must be approved by the administrator.") }
                                    } else {
                                        val session=SupabaseClient.Session(access,obj.optString("refresh_token"),user.optString("id"),user.optString("email",e))
                                        val profile=cloud.fetchProfile(session.accessToken,session.userId)
                                        runOnUiThread {
                                            saveSession(session)
                                            if(profile.optString("account_status")=="approved") {
                                                cloudRole=profile.optString("role","investor")
                                                p.edit().putString("account_name",profile.optString("username",n)).putString("account_email",e).putBoolean("logged",true).apply()
                                                routeToKycOrDashboard(session, profile.optString("username", n))
                                            } else { toast("Account created. Confirm your email, then wait for administrator account approval."); auth(false) }
                                        }
                                    }
                                } else {
                                    val session=cloud.signIn(e,pw)
                                    val profile=cloud.fetchProfile(session.accessToken,session.userId)
                                    runOnUiThread {
                                        if(profile.optString("account_status")!="approved") {
                                            toast("Your account is "+profile.optString("account_status","pending")+". Please confirm your email and wait for administrator approval.")
                                        } else {
                                            saveSession(session);cloudRole=profile.optString("role","investor")
                                            val displayName=profile.optString("username").ifBlank { profile.optString("full_name","Investor") }
                                            p.edit().putString("account_name",displayName).putString("account_email",session.email).putBoolean("logged",true).apply()
                                            routeToKycOrDashboard(session, displayName)
                                        }
                                    }
                                }
                            } catch(e:Exception) {
                                runOnUiThread { toast("Cloud connection failed: "+(e.message ?: "Please try again.")) }
                            } finally {
                                hideLoading()
                            }
                        }
                    }
                }
            },mp(18))
            if(!register) addView(btn("Forgot password?",false){
                val e=email.text.toString().trim().lowercase(Locale.ROOT)
                if(!e.contains("@") || !e.contains(".")) toast("Enter your account email first.")
                else thread {
                    try { cloud.requestPasswordReset(e); runOnUiThread { toast("If this email belongs to an account, a reset email will be sent. Check your inbox and spam folder.") } }
                    catch(ex:Exception) { runOnUiThread { toast("Could not request password reset: "+(ex.message ?: "Please try again.")) } }
                }
            },mp(8))
            addView(btn(if(register)"Already registered? Sign in" else "Create a new account",false){auth(!register)},mp(8))
        },mp(16))
        page.addView(txt("Your account is protected by email/password sign-in. Accounts and balances require the configured cloud service, and new accounts need administrator approval.",12,muted,false,18))
        showPage(scroll)
    }
    private fun resetPasswordScreen(accessToken:String) {
        val(scroll,page)=root("Set a new password","Choose a new password for your Evercrest Capital account")
        val password=input("New password",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val confirm=input("Confirm new password",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        page.addView(card().apply {
            addView(password); addView(confirm,mp(12))
            addView(btn("Update password",true){
                val a=password.text.toString(); val b=confirm.text.toString()
                if(a.length<8) toast("Password must contain at least 8 characters.")
                else if(a!=b) toast("The passwords do not match.")
                else thread {
                    try {
                        cloud.updatePassword(accessToken,a)
                        runOnUiThread {
                            toast("Password updated. Please sign in with your new password.")
                            p.edit().remove("cloud_access_token").remove("cloud_refresh_token").remove("cloud_user_id").putBoolean("logged",false).apply()
                            auth(false)
                        }
                    } catch(e:Exception) { runOnUiThread { toast("Password update failed: "+(e.message ?: "Request a new reset email and try again.")) } }
                }
            },mp(16))
        },mp(20))
        showPage(scroll)
    }

    private fun routeToKycOrDashboard(session: SupabaseClient.Session, displayName: String) {
        saveSession(session)
        latestKycStatus = "not_submitted"
        val token = session.accessToken
        thread {
            try {
                val latestStatus = if (cloudRole == "admin") "approved" else cloud.fetchLatestKycStatus(token, session.userId)
                runOnUiThread {
                    latestKycStatus = latestStatus
                    kycApproved = cloudRole == "admin" || latestStatus == "approved"
                    if (kycApproved) loadCloudWalletThenDashboard()
                    else kycOnboardingScreen(latestStatus, displayName)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    kycApproved = false
                    latestKycStatus = "not_submitted"
                    toast("Could not check identity verification status: " + (e.message ?: "Cloud error"))
                    kycOnboardingScreen("not_submitted", displayName)
                }
            }
        }
    }

    private fun kycOnboardingScreen(status: String, displayName: String) {
        val (screen, page) = root("Identity verification", "Complete verification before accessing dashboard features.")
        page.addView(txt("Welcome, " + displayName, 20, ink, true, 12))
        val message = when (status) {
            "pending" -> "Your KYC documents have been submitted and are waiting for administrator review. Dashboard features remain locked until approval."
            "rejected" -> "Your previous verification was rejected. Review your details and submit a new verification."
            "resubmission_required" -> "The administrator requested a clearer or corrected submission. Please submit your verification again."
            else -> "Your account email is verified and your account is approved. Complete the identity details and upload your ID and face photo. The administrator must approve your KYC before dashboard access is enabled."
        }
        page.addView(card().apply {
            addView(txt("KYC review status: " + status.replace('_', ' ').uppercase(Locale.US), 14, green, true))
            addView(txt(message, 13, muted, false, 8))
        }, mp(18))
        if (status != "pending") {
            val legalName = input("Full legal name")
            val country = input("Country")
            val dob = input("Date of birth (YYYY-MM-DD)")
            val address = input("Residential address")
            val phone = input("Phone number")
            val idType = Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                    arrayOf("National ID", "Passport", "Driver's licence", "Voter's card"))
            }
            page.addView(card().apply {
                addView(txt("Personal information", 16, ink, true))
                addView(legalName, mp(10)); addView(country, mp(8)); addView(dob, mp(8))
                addView(address, mp(8)); addView(phone, mp(8))
                addView(txt("Identity document type", 13, muted, true, 12)); addView(idType, mp(4))
            }, mp(14))
            page.addView(card().apply {
                addView(txt("Identity documents", 16, ink, true))
                addView(txt("Upload a clear government-issued ID image and a separate face/passport photo. These files are stored in private cloud storage.", 12, muted, false, 6))
                addView(btn("Choose ID image", false) { pickKycImage(1001) }, mp(10))
                addView(txt(if (selectedIdImageUri == null) "No ID image selected" else "ID image selected", 12, green, true, 4))
                addView(btn("Choose face/passport photo", false) { pickKycImage(1002) }, mp(8))
                addView(txt(if (selectedFaceImageUri == null) "No face photo selected" else "Face photo selected", 12, green, true, 4))
                addView(btn("Submit KYC for approval", true) {
                    val token = cloudAccessToken ?: p.getString("cloud_access_token", null)
                    val uid = cloudUserId ?: p.getString("cloud_user_id", null)
                    val legal = legalName.text.toString().trim()
                    val c = country.text.toString().trim()
                    val d = dob.text.toString().trim()
                    val a = address.text.toString().trim()
                    val ph = phone.text.toString().trim()
                    val idUri = selectedIdImageUri
                    val faceUri = selectedFaceImageUri
                    if (token == null || uid == null) toast("Please sign in again.")
                    else if (legal.length < 2 || c.isBlank() || !d.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) || a.isBlank() || ph.isBlank()) {
                        toast("Complete all personal details. Use YYYY-MM-DD for date of birth.")
                    } else if (idUri == null || faceUri == null) toast("Choose both the ID image and face photo.")
                    else thread {
                        try {
                            cloud.updateProfile(token, legal, c, d, a, ph)
                            val idBytes = contentResolver.openInputStream(idUri)?.use { it.readBytes() } ?: throw Exception("Could not read ID image.")
                            val faceBytes = contentResolver.openInputStream(faceUri)?.use { it.readBytes() } ?: throw Exception("Could not read face image.")
                            val idPath = cloud.uploadKycImage(token, uid, "id", idBytes, contentResolver.getType(idUri) ?: "image/jpeg")
                            val facePath = cloud.uploadKycImage(token, uid, "face", faceBytes, contentResolver.getType(faceUri) ?: "image/jpeg")
                            cloud.submitKyc(token, uid, idType.selectedItem.toString(), idPath, facePath)
                            // Use the KYC face photo as the user's avatar; it becomes visible after KYC approval unlocks the dashboard.
                            cloud.updateAvatarPath(token, facePath)
                            runOnUiThread {
                                selectedIdImageUri = null; selectedFaceImageUri = null
                                kycApproved = false
                                latestKycStatus = "pending"
                                toast("KYC submitted. Dashboard access will open after administrator approval.")
                                kycOnboardingScreen("pending", displayName)
                            }
                        } catch (e: Exception) {
                            runOnUiThread { toast("KYC submission failed: " + (e.message ?: "Cloud error")) }
                        }
                    }
                }, mp(14))
            }, mp(12))
        }
        page.addView(btn("Refresh verification status", false) {
            val token = cloudAccessToken ?: p.getString("cloud_access_token", null)
            val uid = cloudUserId ?: p.getString("cloud_user_id", null)
            if (token == null || uid == null) { auth(false); return@btn }
            thread {
                try {
                    val current = cloud.fetchLatestKycStatus(token, uid)
                    runOnUiThread {
                        latestKycStatus = current
                        if (current == "approved") { kycApproved = true; loadCloudWalletThenDashboard() }
                        else kycOnboardingScreen(current, displayName)
                    }
                } catch (e: Exception) { runOnUiThread { toast("Could not refresh verification: " + (e.message ?: "Cloud error")) } }
            }
        }, mp(14))
        page.addView(btn("Sign out", false) {
            p.edit().remove("cloud_access_token").remove("cloud_refresh_token").remove("cloud_user_id").putBoolean("logged", false).apply()
            cloudAccessToken = null; cloudRefreshToken = null; cloudUserId = null; kycApproved = false
            latestKycStatus = "not_submitted"; cloudRole = "investor"
            auth(false)
        }, mp(10))
        showPage(screen, true)
    }

    private fun dashboard(){
        if (!kycApproved && cloudRole != "admin") {
            kycOnboardingScreen(latestKycStatus, p.getString("account_name", "Investor") ?: "Investor")
            return
        }
        val first=p.getString("account_name","Investor")?.substringBefore(" ")?: "Investor"
        val(scroll,page)=root("Your dashboard",null,false)
        val welcomeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(txt("Welcome, " + first, 20, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
            val avatar = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_launcher_foreground)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.rgb(228, 235, 231)); shape = android.graphics.drawable.GradientDrawable.OVAL
                    setStroke(dp(1), Color.rgb(204, 214, 208))
                }
                clipToOutline = true
                setPadding(dp(5), dp(5), dp(5), dp(5))
                setOnClickListener { profile() }
            }
            addView(avatar, LinearLayout.LayoutParams(dp(46), dp(46)))
            val tokenForAvatar = cloudAccessToken ?: p.getString("cloud_access_token", null)
            val uidForAvatar = cloudUserId ?: p.getString("cloud_user_id", null)
            if (tokenForAvatar != null && uidForAvatar != null) thread {
                try {
                    val profileData = cloud.fetchProfileDetails(tokenForAvatar, uidForAvatar)
                    // Prefer the saved avatar; for existing accounts, fall back to the latest KYC face photo.
                    val savedPath = profileData.optString("avatar_image_path")
                    val path = if (savedPath.isNotBlank()) savedPath else cloud.fetchLatestKycFacePath(tokenForAvatar, uidForAvatar)
                    if (path.isNotBlank()) {
                        val signed = cloud.signedKycUrl(tokenForAvatar, path)
                        val bitmap = URL(signed).openStream().use { BitmapFactory.decodeStream(it) }
                        if (bitmap != null) runOnUiThread { avatar.setImageBitmap(bitmap); avatar.setPadding(0,0,0,0) }
                    }
                } catch (_: Exception) { }
            }
        }
        page.addView(welcomeRow, mp(8))
        val deposited=amt("deposited");val capital=amt("capital");val profits=amt("profits");val withdrawals=amt("withdrawals")
        val available=(deposited-capital-withdrawals).coerceAtLeast(0f)
        page.addView(LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(20),dp(20),dp(18));elevation=dp(5).toFloat()
            background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(9,22,42),Color.rgb(18,52,75),Color.rgb(17,91,78))).apply{cornerRadius=dp(26).toFloat();setStroke(dp(1),Color.rgb(52,102,112))}
            addView(LinearLayout(this@MainActivity).apply{
                orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL
                addView(txt("PORTFOLIO OVERVIEW",11,Color.rgb(177,221,212),true),LinearLayout.LayoutParams(0,-2,1f))
                addView(txt("USD  •  LIVE ACCOUNT",9,Color.rgb(235,204,129),true).apply{
                    background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.argb(35,255,255,255));cornerRadius=dp(20).toFloat()}
                    setPadding(dp(9),dp(6),dp(9),dp(6))
                })
            })
            addView(txt("TOTAL PROFIT",11,Color.rgb(166,231,204),true,18))
            addView(txt(money(profits),38,Color.WHITE,true,2))
            addView(txt("Recorded profit balance",12,Color.rgb(204,225,226),false,3))
            addView(View(this@MainActivity).apply{background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.argb(90,196,220,223));cornerRadius=dp(2).toFloat()};layoutParams=LinearLayout.LayoutParams(-1,dp(1)).apply{topMargin=dp(18);bottomMargin=dp(14)}})
            addView(LinearLayout(this@MainActivity).apply{
                orientation=LinearLayout.HORIZONTAL
                addView(metricTile("DEPOSITED",money(deposited),Color.rgb(37,76,91)),LinearLayout.LayoutParams(0,-2,1f))
                addView(gap(9),LinearLayout.LayoutParams(dp(9),1))
                addView(metricTile("CAPITAL",money(capital),Color.rgb(37,76,91)),LinearLayout.LayoutParams(0,-2,1f))
            })
            addView(LinearLayout(this@MainActivity).apply{
                orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(9),0,0)
                addView(metricTile("AVAILABLE",money(available),Color.rgb(37,76,91)),LinearLayout.LayoutParams(0,-2,1f))
                addView(gap(9),LinearLayout.LayoutParams(dp(9),1))
                addView(metricTile("WITHDRAWN",money(withdrawals),Color.rgb(37,76,91)),LinearLayout.LayoutParams(0,-2,1f))
            })
        },mp(18))
        page.addView(txt("Quick actions",19,ink,true,24))
        page.addView(LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;addView(btn("Withdraw",true){withdraw()},LinearLayout.LayoutParams(0,dp(48),1f));addView(gap(10),LinearLayout.LayoutParams(dp(10),1));addView(btn("Investment plans",false){plans()},LinearLayout.LayoutParams(0,dp(48),1f))},mp(12))
        page.addView(txt("Companies associated with Elon Musk",19,ink,true,24))
        page.addView(txt("Explore company information and official links",13,muted,false,5))
        page.addView(card().apply {
            addView(txt("Investment Notice",15,ink,true))
            addView(txt("Evercrest Capital Investments provides investment opportunities related to five featured companies associated with Elon Musk.",12,muted,false,8))
            addView(txt("Investment involves risk, including possible loss of capital.",10,Color.rgb(145,155,165),false,2))
        },mp(12))
        val muskCompanies = listOf(
            Triple("Tesla, Inc.","Electric vehicles, energy storage and AI • NASDAQ: TSLA","https://ir.tesla.com/"),
            Triple("SpaceX / Starlink","Space launch, satellite internet and Starlink services • privately held","https://www.spacex.com/"),
            Triple("xAI","Artificial intelligence and Grok • subsidiary of SpaceX; no separate public ticker","https://x.ai/"),
            Triple("Neuralink","Brain-computer interface research • privately held; public shares are not generally available","https://neuralink.com/"),
            Triple("The Boring Company","Tunnel and infrastructure technology • privately held","https://www.boringcompany.com/")
        )
        muskCompanies.forEachIndexed { index, company ->
            page.addView(card().apply {
                setPadding(dp(10),dp(10),dp(10),dp(14))
                addView(companyBanner(company.first,company.second,index),LinearLayout.LayoutParams(-1,dp(142)))
                addView(txt(company.first,17,ink,true).apply{setPadding(dp(7),dp(13),dp(7),0)})
                addView(txt(company.second,12,muted,false,6).apply{setPadding(dp(7),0,dp(7),0)})
                addView(btn("Explore company  ↗",false) {
                    openExternalUrl(company.third)
                },LinearLayout.LayoutParams(-1,dp(46)).apply{topMargin=dp(12);leftMargin=dp(6);rightMargin=dp(6)})
            },mp(12))
        }
        page.addView(txt("Company ownership, listing status and investment availability can change. Always verify current information directly with the company and your licensed broker.",11,muted,false,8))
        page.addView(txt("Your active investments",19,ink,true,24))
        val investmentContainer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        investmentContainer.addView(card().apply{addView(txt("Loading active investments from your cloud account…",13,muted,false))},mp(8))
        page.addView(investmentContainer)
        val token=cloudAccessToken ?: p.getString("cloud_access_token",null)
        if(token!=null) thread {
            try {
                val investments=cloud.fetchInvestments(token)
                runOnUiThread {
                    investmentContainer.removeAllViews()
                    val active=(0 until investments.length()).mapNotNull{investments.optJSONObject(it)}.filter{it.optString("status")=="active"}
                    if(active.isEmpty()) {
                        investmentContainer.addView(card().apply{
                            addView(txt("No active investments yet",16,ink,true))
                            addView(txt("Once the administrator approves an investment, it will appear here automatically.",12,muted,false,5))
                            addView(btn("Explore investment plans",false){plans()},mp(10))
                        },mp(8))
                    } else active.forEach { inv ->
                        val plan=inv.optJSONObject("investment_plans")
                        val planName=plan?.optString("name")?.takeIf{!it.isNullOrBlank()} ?: inv.optString("plan_name","Investment plan")
                        val planType=plan?.optString("slug")?.takeIf{!it.isNullOrBlank()} ?: planName
                        val principal=inv.optDouble("principal_amount",0.0).toFloat()
                        val projected=inv.optDouble("fixed_profit_amount",principal*0.60).toFloat()
                        investmentContainer.addView(card().apply{
                            addView(txt(planName,17,ink,true))
                            addView(txt("Investment type: "+planType.replace("-"," ").uppercase(Locale.US),12,muted,false,5))
                            addView(txt("Active amount: "+money(principal),15,ink,true,10))
                            addView(txt("Illustrative 60% profit estimate: "+money(projected),13,green,true,6))
                            addView(LinearLayout(this@MainActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = android.view.Gravity.CENTER_VERTICAL
                                setPadding(dp(10), dp(9), dp(10), dp(9))
                                background = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(pale)
                                    cornerRadius = dp(12).toFloat()
                                    setStroke(dp(1), Color.rgb(211, 234, 222))
                                }
                                addView(txt("ⓘ", 13, green, true), LinearLayout.LayoutParams(dp(22), -2))
                                addView(txt("Estimate only — returns are not guaranteed and are not credited to withdrawable profit.", 11, muted, false),
                                    LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) })
                            }, mp(8))
                        },mp(8))
                    }
                }
            } catch(e:Exception) {
                runOnUiThread {
                    investmentContainer.removeAllViews()
                    investmentContainer.addView(card().apply{addView(txt("Could not sync active investments: "+(e.message ?: "Cloud error"),12,muted,false))},mp(8))
                }
            }
        }
        page.addView(txt("Account",19,ink,true,24))
        page.addView(LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;addView(btn("Transactions",false){transactions()},LinearLayout.LayoutParams(0,dp(46),1f));addView(gap(8),LinearLayout.LayoutParams(dp(8),1));addView(btn("Profile",false){profile()},LinearLayout.LayoutParams(0,dp(46),1f))},mp(12))
        showPage(scroll, true)
    }
    private data class Plan(val name:String,val icon:String,val min:Float,val tagline:String,val details:String)
    private val planList=listOf(Plan("Bronze","🥉",250f,"A simple starting tier","Portfolio overview and transaction tracking"),Plan("Silver","🥈",500f,"For growing portfolios","Portfolio tracking and plan history"),Plan("Gold","🥇",1000f,"For established investors","Expanded portfolio summaries"),Plan("Platinum","💎",5000f,"For larger portfolios","Full plan overview and account reporting"),Plan("Diamond","💠",10000f,"A premium investment tier","Enhanced portfolio reporting and history"),Plan("Elite","👑",25000f,"The highest membership tier","Premium account overview and reporting"))
    private fun plans(){
        val(scroll,page)=root("Investment plans","Review Evercrest account plans and the important stock-investment distinction")
        val token=cloudAccessToken ?: p.getString("cloud_access_token",null)
        val loading=txt("Loading plans from your cloud account…",13,muted,false,12)
        page.addView(card().apply {
            addView(txt("About the featured companies",15,ink,true))
            addView(txt("Evercrest allocates deposits and plan contributions through its offline office operations toward shares or other eligible investment interests associated with Tesla, SpaceX / Starlink, xAI, Neuralink, and The Boring Company. Not every featured company offers publicly traded shares, and investment eligibility may vary.",12,muted,false,8))
        },mp(12))
        page.addView(loading)
        page.addView(btn("Back to dashboard",false){dashboard()},mp(18))
        showPage(scroll)
        if(token==null){loading.text="Please sign in to load plans.";return}
        thread {
            try {
                val plans=cloud.fetchPlans(token)
                runOnUiThread {
                    loading.visibility=View.GONE
                    if(plans.length()==0) page.addView(txt("No active plans are currently available.",13,muted,false,12))
                    for(i in 0 until plans.length()) {
                        val plan=plans.optJSONObject(i) ?: continue
                        val name=plan.optString("name")
                        val slug=plan.optString("slug")
                        val minimum=plan.optDouble("minimum_amount",0.0).toFloat()
                        val rate=plan.optDouble("fixed_profit_rate",0.0)
                        page.addView(card().apply {
                            addView(txt(plan.optString("icon")+"  "+name,21,ink,true))
                            addView(txt("Minimum investment: "+money(minimum),16,ink,true,12))
                            addView(txt("Configured rate: "+String.format(Locale.US,"%.2f",rate)+"% once; not weekly or monthly.",12,muted,false,6))
                            addView(txt("Configured rates are not guaranteed returns. Requests are reviewed by the administrator; no money moves when you submit.",11,muted,false,8))
                            addView(btn("Submit "+name+" request",true) {
                                val available=(amt("deposited")-amt("capital")-amt("withdrawals")).coerceAtLeast(0f)
                                if(available<minimum) toast("Available balance: "+money(available)+". "+name+" requires "+money(minimum)+".")
                                else thread {
                                    try {
                                        cloud.requestInvestment(token,slug,minimum.toDouble())
                                        runOnUiThread { toast(name+" investment request submitted for administrator review. No funds have been transferred.");loadCloudWalletThenDashboard() }
                                    } catch(e:Exception) { runOnUiThread { toast("Investment request failed: "+(e.message ?: "Please try again.")) } }
                                }
                            },mp(12))
                        },mp(14))
                    }
                }
            } catch(e:Exception) {
                runOnUiThread { loading.text="Unable to load cloud plans: "+(e.message ?: "Check the database setup and try again.") }
            }
        }
    }
    private fun withdraw(){
        val(scroll,page)=root("Withdraw funds","Submit a withdrawal request")
        val available=(amt("deposited")-amt("capital")-amt("withdrawals")).coerceAtLeast(0f)
        page.addView(card().apply{
            addView(txt("Available balance",12,muted,true));addView(txt(money(available),25,ink,true,8))
            val value=input("Amount in USD",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL);addView(value,mp(16))
            addView(txt("Requests are sent to the cloud for administrator review. Submission does not transfer money.",12,muted,false,8))
            addView(btn("Submit withdrawal request",true){
                val v=value.text.toString().toFloatOrNull()
                when{v==null||v<=0f->toast("Enter a valid amount.");v>available->toast("The amount exceeds your available balance.");else->{
                    val token=cloudAccessToken ?: p.getString("cloud_access_token",null)
                    if(token==null) toast("Please sign in to your cloud account first.")
                    else thread {
                        try {
                            cloud.requestWithdrawal(token,v.toDouble())
                            runOnUiThread { toast("Withdrawal request submitted for administrator review. No money has been transferred.");loadCloudWalletThenDashboard() }
                        } catch(e:Exception) { runOnUiThread { toast("Withdrawal request failed: "+(e.message ?: "Please try again.")) } }
                    }
                }}
            },mp(16))
        },mp(22))
        page.addView(btn("Cancel",false){dashboard()},mp(12));showPage(scroll)
    }
    private fun transactions(){
        val(scroll,page)=root("Transactions","Cloud-synced account activity")
        val token=cloudAccessToken ?: p.getString("cloud_access_token",null)
        val uid=cloudUserId ?: p.getString("cloud_user_id",null)
        val status=txt("Loading transactions…",13,muted,false,12);page.addView(status)
        page.addView(btn("Back",false){dashboard()},mp(18))
        showPage(scroll)
        if(token==null||uid==null){status.text="Please sign in to your cloud account.";return}
        thread{
            try{
                val rows=cloud.fetchTransactions(token,uid)
                runOnUiThread{
                    status.text=if(rows.length()==0)"No cloud transactions yet." else "Recent transactions"
                    for(i in 0 until rows.length()){
                        val item=rows.optJSONObject(i)?:continue
                        page.addView(card().apply{
                            val rawType = item.optString("transaction_type").lowercase(Locale.ROOT)
                            val displayType = when {
                                rawType.contains("deposit") -> "Deposit"
                                rawType.contains("withdraw") -> "Withdrawal"
                                rawType.contains("invest") -> "Investment"
                                else -> "Transaction"
                            }
                            addView(txt(displayType,15,ink,true))
                            addView(txt(money(item.optDouble("amount").toFloat()),16,green,true,8))
                            addView(txt("Status: "+item.optString("status"),12,muted,false,4))
                            addView(txt(item.optString("created_at"),11,muted,false,4))
                        },mp(10))
                    }
                }
            }catch(e:Exception){runOnUiThread{status.text="Unable to load cloud transactions: "+(e.message ?: "Cloud error")}}
        }
    }
    private fun profile(){
        val(scroll,page)=root("Your account","Personal details and identity verification")
        val token=cloudAccessToken ?: p.getString("cloud_access_token",null)
        val uid=cloudUserId ?: p.getString("cloud_user_id",null)
        val fullName=input("Full legal name")
        val country=input("Country")
        val dob=input("Date of birth (YYYY-MM-DD)")
        val address=input("Residential address")
        val phone=input("Phone number")
        val profileStatus=txt("Loading your cloud profile…",12,muted,false,8)
        page.addView(card().apply{
            addView(txt(p.getString("account_name","Investor")?:"Investor",19,ink,true))
            addView(txt(p.getString("account_email","")?:"",14,muted,false,5))
            addView(txt("Your profile photo is taken automatically from the face photo submitted with KYC and appears here after identity approval.",12,muted,false,8))
            addView(profileStatus)
            addView(txt("Personal details",16,ink,true,14))
            addView(fullName,mp(8));addView(country,mp(8));addView(dob,mp(8));addView(address,mp(8));addView(phone,mp(8))
            addView(btn("Save personal details",true){
                if(token==null){toast("Please sign in again.");return@btn}
                val n=fullName.text.toString().trim()
                val c=country.text.toString().trim()
                val d=dob.text.toString().trim()
                val a=address.text.toString().trim()
                val ph=phone.text.toString().trim()
                if(n.length<2||c.isBlank()||!d.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))||a.isBlank()||ph.isBlank()){
                    toast("Complete all details and use YYYY-MM-DD for date of birth.")
                } else thread{
                    try{cloud.updateProfile(token,n,c,d,a,ph);runOnUiThread{toast("Personal details saved to the cloud.");profileStatus.text="Profile details saved."}}
                    catch(e:Exception){runOnUiThread{toast("Profile update failed: "+(e.message ?: "Cloud error"))}}
                }
            },mp(14))
        },mp(16))
        page.addView(txt("Identity verification",18,ink,true,18))
        page.addView(card().apply{
            addView(txt("Choose one image showing one side of your government-issued ID, and one clear face/passport photo. Images are uploaded to private cloud storage.",12,muted,false))
            addView(btn("Choose ID image",false){pickKycImage(1001)},mp(12))
            addView(txt(if(selectedIdImageUri==null)"No ID image selected" else "ID image selected",12,green,true,6))
            addView(btn("Choose face/passport photo",false){pickKycImage(1002)},mp(12))
            addView(txt(if(selectedFaceImageUri==null)"No face photo selected" else "Face photo selected",12,green,true,6))
            val docType=Spinner(this@MainActivity).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("National ID","Passport","Driver's licence","Voter's card"))}
            addView(txt("ID document type",13,muted,true,12));addView(docType,mp(4))
            addView(btn("Submit identity verification",true){
                val idUri=selectedIdImageUri;val faceUri=selectedFaceImageUri
                if(token==null||uid==null)toast("Please sign in again.")
                else if(idUri==null||faceUri==null)toast("Choose both the ID image and face photo.")
                else thread{
                    try{
                        val idBytes=contentResolver.openInputStream(idUri)?.use{it.readBytes()}?:throw Exception("Could not read ID image.")
                        val faceBytes=contentResolver.openInputStream(faceUri)?.use{it.readBytes()}?:throw Exception("Could not read face image.")
                        val idPath=cloud.uploadKycImage(token,uid,"id",idBytes,contentResolver.getType(idUri)?: "image/jpeg")
                        val facePath=cloud.uploadKycImage(token,uid,"face",faceBytes,contentResolver.getType(faceUri)?: "image/jpeg")
                        cloud.submitKyc(token,uid,docType.selectedItem.toString(),idPath,facePath)
                        cloud.updateAvatarPath(token,facePath)
                        runOnUiThread{toast("Identity documents submitted for administrator review. Your KYC face photo will be used as your profile photo after approval.");selectedIdImageUri=null;selectedFaceImageUri=null;profile()}
                    }catch(e:Exception){runOnUiThread{toast("Identity submission failed: "+(e.message ?: "Cloud error"))}}
                }
            },mp(14))
        },mp(10))
        if(cloudRole=="admin") page.addView(btn("Administrator tools",true){adminDashboard()},mp(12))
        page.addView(btn("Refresh cloud account",false){loadCloudWalletThenDashboard()},mp(12))
        page.addView(btn("Sign out",false){
            val current=cloudAccessToken
            p.edit().remove("cloud_access_token").remove("cloud_refresh_token").remove("cloud_user_id").putBoolean("logged",false).apply()
            cloudAccessToken=null;cloudRefreshToken=null;cloudUserId=null;kycApproved=false
            latestKycStatus="not_submitted";cloudRole="investor"
            thread{try{if(current!=null)cloud.signOut(current)}catch(_:Exception){}}
            auth(false)
        },mp(18))
        page.addView(btn("Back to dashboard",false){dashboard()},mp(10))
        showPage(scroll)
        if(token!=null&&uid!=null) thread{
            try{
                val data=cloud.fetchProfileDetails(token,uid)
                runOnUiThread{
                    fullName.setText(data.optString("full_name"))
                    country.setText(data.optString("country"))
                    dob.setText(data.optString("date_of_birth"))
                    address.setText(data.optString("address"))
                    phone.setText(data.optString("phone"))
                    profileStatus.text="Account status: "+data.optString("account_status")
                }
            }catch(e:Exception){runOnUiThread{profileStatus.text="Could not load profile: "+(e.message ?: "Cloud error")}}
        }
    }
    private fun pickKycImage(requestCode:Int){
        val intent=Intent(Intent.ACTION_GET_CONTENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)}
        startActivityForResult(Intent.createChooser(intent,"Choose image"),requestCode)
    }
    @Deprecated("Use the image picker result API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode==RESULT_OK&&data?.data!=null){
            if(requestCode==1001) selectedIdImageUri=data.data
            if(requestCode==1002) selectedFaceImageUri=data.data
            if(requestCode==1003) selectedAvatarImageUri=data.data
            profile()
        }
    }
    private fun adminDashboard(query:String=""){
        if(cloudRole!="admin"){toast("Administrator access required.");return}
        val(scroll,page)=root("Administrator tools","Cloud-managed accounts and audited balance adjustments")
        val search=input("Search users by email",InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS).apply{setText(query)}
        page.addView(card().apply{
            addView(search)
            addView(btn("Search users",true){adminDashboard(search.text.toString().trim())},mp(10))
        },mp(16))
        page.addView(btn("Manage investment plans",false){adminPlanManagement()},mp(8))
        val token=cloudAccessToken ?: p.getString("cloud_access_token",null)
        if(token==null){page.addView(txt("Please sign in again.",13,muted,false,12));showPage(scroll);return}
        val loading=txt("Loading cloud users…",13,muted,false,14);page.addView(loading)
        showPage(scroll)
        thread{
            try{
                val users=cloud.searchUsers(token,query)
                runOnUiThread{
                    loading.text=if(users.length()==0)"No users found." else "Users ("+users.length()+")"
                    for(i in 0 until users.length()){
                        val user=users.optJSONObject(i)?:continue
                        val id=user.optString("id")
                        page.addView(card().apply{
                            addView(txt(user.optString("full_name","Investor"),16,ink,true))
                            addView(txt(user.optString("email"),12,muted,false,4))
                            addView(txt("Role: "+user.optString("role")+" • Status: "+user.optString("account_status"),12,green,true,8))
                            addView(btn("Approve account",false){
                                changeUserStatus(id,"approved","Approved by administrator",query)
                            },mp(8))
                            addView(btn("Require resubmission",false){
                                changeUserStatus(id,"resubmission_required","Please resubmit required information",query)
                            },mp(6))
                            addView(btn("Suspend account",false){
                                changeUserStatus(id,"suspended","Suspended by administrator",query)
                            },mp(6))
                            addView(btn("Select for balance adjustment",true){
                                adminBalanceEditor(id,user.optString("email"),query)
                            },mp(8))
                        },mp(10))
                    }
                }
            }catch(e:Exception){runOnUiThread{loading.text="Unable to load users: "+(e.message ?: "Cloud error")}}
        }
        val investmentHeading=txt("Pending investment requests",18,ink,true,24)
        page.addView(investmentHeading)
        val investmentStatus=txt("Loading pending requests…",13,muted,false,8);page.addView(investmentStatus)
        thread{
            try{
                val pending=cloud.fetchPendingInvestments(token)
                runOnUiThread{
                    investmentStatus.text=if(pending.length()==0)"No pending investment requests." else "Requests ("+pending.length()+")"
                    for(i in 0 until pending.length()){
                        val inv=pending.optJSONObject(i)?:continue
                        val investmentId=inv.optString("id")
                        val investorId=inv.optString("user_id")
                        page.addView(card().apply{
                            addView(txt("Investment request",15,ink,true))
                            addView(txt("Investor ID: "+investorId,11,muted,false,4))
                            addView(txt("Principal: "+money(inv.optDouble("principal_amount").toFloat()),16,green,true,8))
                            addView(txt("Configured profit estimate: "+money(inv.optDouble("fixed_profit_amount").toFloat())+" (not guaranteed)",12,muted,false,4))
                            addView(btn("Approve investment",true){
                                reviewInvestment(investmentId,"active","Approved by administrator",query)
                            },mp(8))
                            addView(btn("Reject investment",false){
                                reviewInvestment(investmentId,"rejected","Rejected by administrator",query)
                            },mp(6))
                        },mp(10))
                    }
                }
            }catch(e:Exception){runOnUiThread{investmentStatus.text="Could not load investment requests: "+(e.message ?: "Cloud error")}}
        }
        val withdrawalHeading=txt("Pending withdrawal requests",18,ink,true,24)
        page.addView(withdrawalHeading)
        val withdrawalStatus=txt("Loading withdrawal requests…",13,muted,false,8);page.addView(withdrawalStatus)
        thread{
            try{
                val requests=cloud.fetchPendingWithdrawals(token)
                runOnUiThread{
                    withdrawalStatus.text=if(requests.length()==0)"No pending withdrawal requests." else "Requests ("+requests.length()+")"
                    for(i in 0 until requests.length()){
                        val item=requests.optJSONObject(i)?:continue
                        val txId=item.optString("id")
                        page.addView(card().apply{
                            addView(txt("Withdrawal request",15,ink,true))
                            addView(txt("Investor ID: "+item.optString("user_id"),11,muted,false,4))
                            addView(txt("Amount: "+money(item.optDouble("amount").toFloat()),16,green,true,8))
                            addView(btn("Approve request (payout still manual)",true){
                                reviewWithdrawal(txId,"approved","Approved; payout must be completed manually",query)
                            },mp(8))
                            addView(btn("Reject request",false){
                                reviewWithdrawal(txId,"rejected","Rejected by administrator",query)
                            },mp(6))
                        },mp(10))
                    }
                }
            }catch(e:Exception){runOnUiThread{withdrawalStatus.text="Could not load withdrawals: "+(e.message ?: "Cloud error")}}
        }
        val kycHeading=txt("Pending identity verification",18,ink,true,24)
        page.addView(kycHeading)
        val kycStatus=txt("Loading identity submissions…",13,muted,false,8);page.addView(kycStatus)
        thread{
            try{
                val submissions=cloud.fetchPendingKyc(token)
                runOnUiThread{
                    kycStatus.text=if(submissions.length()==0)"No pending identity submissions." else "Submissions ("+submissions.length()+")"
                    for(i in 0 until submissions.length()){
                        val item=submissions.optJSONObject(i)?:continue
                        val sid=item.optString("id")
                        val idPath=item.optString("id_image_path")
                        val facePath=item.optString("face_image_path")
                        page.addView(card().apply{
                            addView(txt(item.optString("document_type")+" • Identity verification",15,ink,true))
                            addView(txt("Investor ID: "+item.optString("user_id"),11,muted,false,4))
                            addView(btn("View ID image (private link)",false){openKycDocument(idPath)},mp(8))
                            addView(btn("View face photo (private link)",false){openKycDocument(facePath)},mp(6))
                            addView(btn("Approve identity",true){reviewKyc(sid,"approved","Verified by administrator",query)},mp(8))
                            addView(btn("Reject identity",false){reviewKyc(sid,"rejected","Identity documents rejected",query)},mp(6))
                            addView(btn("Request resubmission",false){reviewKyc(sid,"resubmission_required","Please upload clearer identity images",query)},mp(6))
                        },mp(10))
                    }
                }
            }catch(e:Exception){runOnUiThread{kycStatus.text="Could not load identity submissions: "+(e.message ?: "Cloud error")}}
        }
        page.addView(btn("Back to profile",false){profile()},mp(18))
    }
    private fun reviewWithdrawal(id:String,decision:String,reason:String,query:String){
        val token=cloudAccessToken ?: p.getString("cloud_access_token",null) ?: run{toast("Please sign in again.");return}
        thread{
            try{
                cloud.adminReviewWithdrawal(token,id,decision,reason)
                runOnUiThread{
                    toast(if(decision=="approved")"Withdrawal approved. Payment must still be sent manually." else "Withdrawal rejected.")
                    adminDashboard(query)
                }
            }catch(e:Exception){runOnUiThread{toast("Withdrawal review failed: "+(e.message ?: "Cloud error"))}}
        }
    }
    private fun openKycDocument(path:String){
        val token=cloudAccessToken ?: p.getString("cloud_access_token",null) ?: run{toast("Please sign in again.");return}
        thread{
            try{
                val url=cloud.signedKycUrl(token,path)
                runOnUiThread{try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(_:Exception){toast("Could not open the private document.")}}
            }catch(e:Exception){runOnUiThread{toast("Could not open document: "+(e.message ?: "Cloud error"))}}
        }
    }
    private fun reviewKyc(id:String,status:String,reason:String,query:String){
        val token=cloudAccessToken ?: p.getString("cloud_access_token",null) ?: run{toast("Please sign in again.");return}
        thread{
            try{cloud.reviewKyc(token,id,status,reason);runOnUiThread{toast("Identity submission marked "+status+".");adminDashboard(query)}}
            catch(e:Exception){runOnUiThread{toast("KYC review failed: "+(e.message ?: "Cloud error"))}}
        }
    }
    private fun reviewInvestment(id:String,decision:String,reason:String,query:String){
        val token=cloudAccessToken ?: p.getString("cloud_access_token",null) ?: run{toast("Please sign in again.");return}
        thread{
            try{
                cloud.adminReviewInvestment(token,id,decision,reason)
                runOnUiThread{toast("Investment request "+decision+".");adminDashboard(query)}
            }catch(e:Exception){runOnUiThread{toast("Investment review failed: "+(e.message ?: "Cloud error"))}}
        }
    }
    private fun adminPlanManagement() {
        if(cloudRole!="admin"){toast("Administrator access required.");return}
        val token=cloudAccessToken ?: p.getString("cloud_access_token",null)
        val(scroll,page)=root("Investment plan management","Edit the six plans. Changes are restricted to administrators and recorded in the audit log.")
        if(token==null){page.addView(txt("Please sign in again.",13,muted,false,12));showPage(scroll);return}
        val status=txt("Loading plans from cloud…",13,muted,false,10);page.addView(status)
        page.addView(btn("Back to administrator tools",false){adminDashboard()},mp(14))
        showPage(scroll)
        thread {
            try {
                val plans=cloud.fetchAdminPlans(token)
                runOnUiThread {
                    status.text=if(plans.length()==0)"No plans found." else "Loaded "+plans.length()+" plans"
                    for(i in 0 until plans.length()) {
                        val plan=plans.optJSONObject(i) ?: continue
                        val planId=plan.optString("id")
                        val nameField=input("Plan name").apply{setText(plan.optString("name"))}
                        val minField=input("Minimum amount (USD)",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply{setText(plan.optDouble("minimum_amount").toString())}
                        val rateField=input("Configured rate (%)",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply{setText(plan.optDouble("fixed_profit_rate").toString())}
                        val activeSwitch=Switch(this@MainActivity).apply{text="Plan active";isChecked=plan.optBoolean("is_active")}
                        page.addView(card().apply {
                            addView(txt(plan.optString("icon")+"  "+plan.optString("slug").uppercase(Locale.ROOT),15,ink,true))
                            addView(nameField,mp(10));addView(minField,mp(8));addView(rateField,mp(8));addView(activeSwitch,mp(8))
                            addView(txt("Configured rate is not a guarantee of profit.",11,muted,false,6))
                            addView(btn("Save plan changes",true) {
                                val n=nameField.text.toString().trim()
                                val minimum=minField.text.toString().toDoubleOrNull()
                                val rate=rateField.text.toString().toDoubleOrNull()
                                when {
                                    n.length<2 -> toast("Enter a valid plan name.")
                                    minimum==null || minimum<=0 -> toast("Minimum amount must be greater than zero.")
                                    rate==null || rate<0 || rate>100 -> toast("Configured rate must be between 0 and 100.")
                                    else -> thread {
                                        try {
                                            cloud.adminUpdatePlan(token,planId,n,minimum,rate,activeSwitch.isChecked)
                                            runOnUiThread { toast(n+" plan saved.");adminPlanManagement() }
                                        } catch(e:Exception) { runOnUiThread { toast("Plan update failed: "+(e.message ?: "Cloud error")) } }
                                    }
                                }
                            },mp(12))
                        },mp(10))
                    }
                }
            } catch(e:Exception) { runOnUiThread { status.text="Could not load plans: "+(e.message ?: "Cloud error") } }
        }
    }

    private fun changeUserStatus(userId:String,status:String,reason:String,query:String){
        val token=cloudAccessToken ?: p.getString("cloud_access_token",null) ?: run{toast("Please sign in again.");return}
        thread{
            try{
                cloud.adminSetProfileStatus(token,userId,status,reason)
                var emailWarning=false
                try { cloud.notifyAccountStatus(token,userId,status,reason) } catch(_:Exception) { emailWarning=true }
                runOnUiThread{
                    toast(if(emailWarning) "Account status updated, but email notification is not configured or failed." else "Account status updated and notification sent.")
                    adminDashboard(query)
                }
            }catch(e:Exception){runOnUiThread{toast("Status update failed: "+(e.message ?: "Cloud error"))}}
        }
    }
    private fun adminBalanceEditor(userId:String,email:String,query:String){
        val(scroll,page)=root("Adjust account balance",email)
        val field=Spinner(this).apply{
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,
                arrayOf("deposited_amount","capital_amount","profit_amount","withdrawals_amount"))
        }
        val amount=input("New amount in USD",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val reason=input("Reason for audit log")
        page.addView(card().apply{
            addView(txt("Balance field",13,muted,true));addView(field,mp(6))
            addView(txt("New amount (USD)",13,muted,true,12));addView(amount,mp(6))
            addView(txt("Reason (required)",13,muted,true,12));addView(reason,mp(6))
            addView(btn("Save cloud balance",true){
                val v=amount.text.toString().toDoubleOrNull()
                val why=reason.text.toString().trim()
                when{v==null||v<0->toast("Enter a valid non-negative amount.");why.length<4->toast("Enter a reason for the audit log.");else->{
                    val token=cloudAccessToken ?: p.getString("cloud_access_token",null)
                    if(token==null)toast("Please sign in again.")
                    else thread{
                        try{
                            cloud.adminAdjustWallet(token,userId,field.selectedItem.toString(),v,why)
                            runOnUiThread{toast("Cloud balance updated and audit logged.");adminDashboard(query)}
                        }catch(e:Exception){runOnUiThread{toast("Balance update failed: "+(e.message ?: "Cloud error"))}}
                    }
                }}
            },mp(16))
        },mp(20))
        page.addView(btn("Cancel",false){adminDashboard(query)},mp(12))
        showPage(scroll)
    }

    private fun manageBalances(){
        val(scroll,page)=root("Balance management","Update the figures shown in this local account")
        page.addView(card().apply{
            val deposited=input("Total deposited in USD",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply{setText(amt("deposited").toString())}
            val profits=input("Profits in USD",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply{setText(amt("profits").toString())}
            addView(txt("Total deposited",13,muted,true));addView(deposited,mp(6))
            addView(txt("Profits",13,muted,true,14));addView(profits,mp(6))
            addView(btn("Save balances",true){
                val d=deposited.text.toString().toFloatOrNull();val pr=profits.text.toString().toFloatOrNull()
                when{d==null||pr==null||d<0||pr<0->toast("Enter valid non-negative amounts.");else->{p.edit().putFloat("deposited",d).putFloat("profits",pr).apply();addTx("Balance updated","Account balance figures",d);toast("Balances updated.");dashboard()}}
            },mp(18))
        },mp(20))
        page.addView(btn("Cancel",false){profile()},mp(12));showPage(scroll)
    }

    private fun addTx(type:String,detail:String,value:Float){
        val a=try{org.json.JSONArray(p.getString("transactions","[]"))}catch(_:Exception){org.json.JSONArray()}
        a.put(JSONObject().put("type",type).put("detail",detail).put("amount",value.toDouble()).put("date",java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(java.util.Date())))
        p.edit().putString("transactions",a.toString()).apply()
    }
    private fun openExternalUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            toast("Could not open the official website on this device.")
        }
    }

    private data class StockQuote(val symbol:String,val name:String,val price:Float,val changePct:Float)
    private fun loadTopStocks(done:(String,List<StockQuote>)->Unit){thread{
        val stocks=listOf("AAPL" to "Apple","MSFT" to "Microsoft","NVDA" to "NVIDIA","AMZN" to "Amazon","GOOGL" to "Alphabet")
        val quotes=mutableListOf<StockQuote>()
        stocks.forEach { (symbol,name) ->
            var connection:HttpURLConnection?=null
            try {
                connection=URL("https://query1.finance.yahoo.com/v8/finance/chart/"+symbol+"?range=5d&interval=1d").openConnection() as HttpURLConnection
                connection.connectTimeout=7000;connection.readTimeout=7000;connection.setRequestProperty("User-Agent","Mozilla/5.0")
                if(connection.responseCode !in 200..299) throw java.io.IOException("HTTP "+connection.responseCode)
                val body=connection.inputStream.bufferedReader().use{it.readText()}
                val result=JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                val closes=result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
                val values=mutableListOf<Float>()
                for(i in 0 until closes.length()) if(!closes.isNull(i)) values.add(closes.getDouble(i).toFloat())
                if(values.isNotEmpty()) {
                    val first=values.first();val last=values.last()
                    quotes.add(StockQuote(symbol,name,last,if(first>0f)(last-first)/first*100f else 0f))
                }
            } catch(_:Exception) {
                // One unavailable symbol should not block the remaining quotes.
            } finally { connection?.disconnect() }
        }
        runOnUiThread {
            done(if(quotes.isEmpty())"Latest available quotes unavailable • pull down to retry" else "Latest available quotes • 5-day change • delayed/subject to provider availability",quotes)
        }
    }}
    private fun refreshPage(title:String) {
        when(title) {
            "Your dashboard" -> { toast("Refreshing cloud account and market data…");loadCloudWalletThenDashboard() }
            "Investment plans" -> { toast("Refreshing investment plans…");plans() }
            "Transactions" -> { toast("Refreshing transactions…");transactions() }
            "Profile" -> { toast("Refreshing profile…");profile() }
            "About Evercrest Capital" -> aboutPage()
            else -> toast("Pull-to-refresh is available on account and data pages.")
        }
    }
    private fun enablePullToRefresh(onRefresh:()->Unit) {
        val scroll=lastScrollView ?: return
        var startY=0f
        var triggered=false
        scroll.setOnTouchListener { _, event ->
            when(event.action) {
                android.view.MotionEvent.ACTION_DOWN -> { startY=event.y;triggered=false }
                android.view.MotionEvent.ACTION_UP -> {
                    val distance=event.y-startY
                    if(!triggered && scroll.scrollY==0 && distance>dp(100)) {
                        triggered=true
                        onRefresh()
                    }
                }
            }
            false
        }
    }
    private inner class MarketLine(context:android.content.Context):View(this){
        private val line=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=green;strokeWidth=dp(3).toFloat();style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
        private val grid=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(229,235,231);strokeWidth=dp(1).toFloat()}
        override fun onDraw(canvas:Canvas){super.onDraw(canvas);val l=dp(4).toFloat();val r=width-dp(4).toFloat();val t=dp(12).toFloat();val b=height-dp(12).toFloat()
            for(i in 0..3)canvas.drawLine(l,t+(b-t)*i/3f,r,t+(b-t)*i/3f,grid)
            val v=marketValues;if(v.size<2)return;val mn=v.minOrNull()?:return;val mx=v.maxOrNull()?:return;val range=(mx-mn).takeIf{it>0}?:1f;val path=Path()
            v.forEachIndexed{i,n->val x=l+(r-l)*i/(v.size-1);val y=b-(n-mn)/range*(b-t);if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};canvas.drawPath(path,line)
        }
    }
    private fun metricTile(label:String,value:String,tileColor:Int)=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(10),dp(12))
        background=android.graphics.drawable.GradientDrawable().apply{setColor(tileColor);cornerRadius=dp(15).toFloat();setStroke(dp(1),Color.argb(65,205,230,229))}
        addView(txt(label,9,Color.rgb(176,211,210),true))
        addView(txt(value,16,Color.WHITE,true,5))
    }
    private fun companyBanner(name:String,detail:String,index:Int):View {
        val palettes=arrayOf(
            intArrayOf(Color.rgb(27,18,24),Color.rgb(130,28,43),Color.rgb(214,67,73)),
            intArrayOf(Color.rgb(7,22,46),Color.rgb(24,59,105),Color.rgb(64,123,171)),
            intArrayOf(Color.rgb(24,14,43),Color.rgb(76,34,119),Color.rgb(133,79,180)),
            intArrayOf(Color.rgb(5,42,42),Color.rgb(17,104,94),Color.rgb(73,165,142)),
            intArrayOf(Color.rgb(42,30,17),Color.rgb(113,76,37),Color.rgb(194,143,69))
        )
        val palette=palettes[index%palettes.size]
        val initials=when(index){0->"T";1->"S";2->"x";3->"N";else->"B"}
        return android.widget.FrameLayout(this).apply{
            background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,palette).apply{cornerRadius=dp(20).toFloat()}
            clipToOutline=true
            addView(View(this@MainActivity).apply{
                background=android.graphics.drawable.GradientDrawable().apply{shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(Color.argb(24,255,255,255))}
            },android.widget.FrameLayout.LayoutParams(dp(150),dp(150),android.view.Gravity.RIGHT or android.view.Gravity.CENTER_VERTICAL).apply{rightMargin=dp(-28)})
            addView(View(this@MainActivity).apply{
                background=android.graphics.drawable.GradientDrawable().apply{shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(Color.argb(22,255,255,255))}
            },android.widget.FrameLayout.LayoutParams(dp(90),dp(90),android.view.Gravity.RIGHT or android.view.Gravity.TOP).apply{rightMargin=dp(55);topMargin=dp(-35)})
            val content=LinearLayout(this@MainActivity).apply{
                orientation=LinearLayout.VERTICAL;gravity=android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(18),dp(14),dp(18),dp(14))
                addView(LinearLayout(this@MainActivity).apply{
                    orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL
                    val mark=TextView(this@MainActivity).apply{
                        text=initials;textSize=27f;gravity=android.view.Gravity.CENTER;setTextColor(Color.WHITE)
                        typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD)
                        background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.argb(45,255,255,255));cornerRadius=dp(15).toFloat();setStroke(dp(1),Color.argb(100,255,255,255))}
                    }
                    addView(mark,LinearLayout.LayoutParams(dp(48),dp(48)))
                    addView(LinearLayout(this@MainActivity).apply{
                        orientation=LinearLayout.VERTICAL;setPadding(dp(11),0,0,0)
                        addView(txt("FEATURED COMPANY",9,Color.rgb(230,232,238),true))
                        addView(txt(name.uppercase(Locale.US),14,Color.WHITE,true,4))
                    },LinearLayout.LayoutParams(0,-2,1f))
                })
                addView(txt("COMPANY PROFILE  •  OFFICIAL INFORMATION",9,Color.rgb(239,224,197),true,dp(14)))
            }
            addView(content,android.widget.FrameLayout.LayoutParams(-1,-1))
        }
    }
    private fun amt(key:String)=p.getFloat(key,0f)
    private fun money(v:Float)="$"+String.format(Locale.US,"%,.2f",v)
    private fun hash(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun input(h:String,type:Int=InputType.TYPE_CLASS_TEXT)=EditText(this).apply{
        hint=h;textSize=15f;setSingleLine(true);inputType=type
        setTextColor(ink);setHintTextColor(Color.rgb(137,149,161));minHeight=dp(52)
        setPadding(dp(16),dp(13),dp(16),dp(13))
        background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.rgb(250,252,253));cornerRadius=dp(15).toFloat();setStroke(dp(1),Color.rgb(218,227,232))}
    }
    private fun card()=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(18))
        background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=dp(20).toFloat();setStroke(dp(1),Color.rgb(229,235,239))}
        elevation=dp(3).toFloat()
    }
    private fun rowMetric(label:String,value:String)=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL;addView(txt(label,13,Color.rgb(218,225,232),false),LinearLayout.LayoutParams(0,-2,1f));addView(txt(value,15,Color.WHITE,true));setPadding(0,dp(5),0,dp(5))}
    private fun txt(s:String,size:Int,color:Int,bold:Boolean,top:Int=0)=TextView(this).apply{text=s;textSize=size.toFloat();setTextColor(color);if(bold)typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD);if(top>0)setPadding(0,dp(top),0,0)}
    private fun btn(label:String,primary:Boolean,click:()->Unit)=Button(this).apply{
        text=label;textSize=14f;isAllCaps=false;minHeight=dp(46)
        typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)
        setTextColor(if(primary)Color.WHITE else Color.rgb(15,111,82))
        backgroundTintList=null
        background=android.graphics.drawable.GradientDrawable().apply{
            setColor(if(primary)Color.rgb(15,130,88) else Color.rgb(239,248,243))
            cornerRadius=dp(15).toFloat()
            if(!primary)setStroke(dp(1),Color.rgb(205,231,218))
        }
        setPadding(dp(16),dp(10),dp(16),dp(10))
        setOnClickListener{click()}
    }
    private fun gap(w:Int)=View(this).apply{layoutParams=LinearLayout.LayoutParams(dp(w),1)}
    private fun mp(top:Int)=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(top)}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}