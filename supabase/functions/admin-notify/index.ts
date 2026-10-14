// Supabase Edge Function: admin-notify
// Deploy with JWT verification enabled. Only an authenticated, approved admin may trigger mail.
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const esc = (value: string) => value.replace(/[&<>"']/g, ch => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
}[ch] ?? ch));

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return Response.json({ error: "Method not allowed" }, { status: 405, headers: corsHeaders });

  const auth = req.headers.get("Authorization") ?? "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  const projectUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const resendKey = Deno.env.get("RESEND_API_KEY");
  const fromEmail = Deno.env.get("FROM_EMAIL");
  if (!projectUrl || !anonKey || !resendKey || !fromEmail) {
    return Response.json({ error: "Email notification service is not configured." }, { status: 503, headers: corsHeaders });
  }
  if (!token) return Response.json({ error: "Sign-in required." }, { status: 401, headers: corsHeaders });

  try {
    const authResponse = await fetch(projectUrl + "/auth/v1/user", {
      headers: { apikey: anonKey, Authorization: "Bearer " + token },
    });
    if (!authResponse.ok) return Response.json({ error: "Invalid session." }, { status: 401, headers: corsHeaders });
    const caller = await authResponse.json();
    const callerRowsResponse = await fetch(projectUrl + "/rest/v1/profiles?select=role,account_status&id=eq." + encodeURIComponent(caller.id), {
      headers: { apikey: anonKey, Authorization: "Bearer " + token },
    });
    if (!callerRowsResponse.ok) return Response.json({ error: "Unable to verify administrator access." }, { status: 403, headers: corsHeaders });
    const callerRows = await callerRowsResponse.json();
    if (!callerRows[0] || callerRows[0].role !== "admin" || callerRows[0].account_status !== "approved") {
      return Response.json({ error: "Administrator access required." }, { status: 403, headers: corsHeaders });
    }

    const body = await req.json();
    const userId = String(body.user_id ?? "");
    const status = String(body.status ?? "");
    const reason = String(body.reason ?? "");
    const allowed = ["approved", "rejected", "resubmission_required", "suspended"];
    if (!userId || !allowed.includes(status)) {
      return Response.json({ error: "A valid user ID and account status are required." }, { status: 400, headers: corsHeaders });
    }
    const targetResponse = await fetch(projectUrl + "/rest/v1/profiles?select=email,full_name&id=eq." + encodeURIComponent(userId), {
      headers: { apikey: anonKey, Authorization: "Bearer " + token },
    });
    if (!targetResponse.ok) return Response.json({ error: "Unable to load recipient." }, { status: 400, headers: corsHeaders });
    const targetRows = await targetResponse.json();
    const target = targetRows[0];
    if (!target?.email) return Response.json({ error: "Recipient email not found." }, { status: 404, headers: corsHeaders });

    const labels: Record<string, string> = {
      approved: "Your account has been approved",
      rejected: "An update about your account",
      resubmission_required: "More information is needed",
      suspended: "Your account status has changed",
    };
    const reasonLine = reason ? "<p>Note from the administrator: " + esc(reason) + "</p>" : "";
    const html = "<p>Hello " + esc(target.full_name || "Investor") + ",</p><p>" + esc(labels[status]) +
      " for MY INVEST.</p>" + reasonLine + "<p>Sign in to the MY INVEST app to review your account status. If you have questions, contact the app administrator.</p><p>MY INVEST</p>";
    const mailResponse = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: { Authorization: "Bearer " + resendKey, "Content-Type": "application/json" },
      body: JSON.stringify({ from: fromEmail, to: [target.email], subject: labels[status], html }),
    });
    if (!mailResponse.ok) {
      const detail = await mailResponse.text();
      console.error("Resend rejected the message", mailResponse.status, detail);
      return Response.json({ error: "Email provider rejected the message." }, { status: 502, headers: corsHeaders });
    }
    return Response.json({ ok: true }, { headers: corsHeaders });
  } catch (error) {
    console.error("admin-notify error", error);
    return Response.json({ error: "Notification failed." }, { status: 500, headers: corsHeaders });
  }
});
