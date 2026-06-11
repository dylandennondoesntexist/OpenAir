import { Hono } from "hono";

type Env = {
  OPENAIR_ENV: string;
};

const app = new Hono<{ Bindings: Env }>();

app.get("/health", (c) => c.json({ ok: true, env: c.env.OPENAIR_ENV }));

app.get("/clips/feed", (c) => {
  const h3Index = c.req.query("h3_index") ?? "882a1072b5fffff";
  const category = c.req.query("category") ?? "all";

  return c.json({
    h3_index: h3Index,
    category,
    expires_in_seconds: 300,
    clips: [
      {
        id: "asbury-boardwalk-01",
        title: "The diner booth Springsteen used to haunt",
        creator_name: "Mara L.",
        category: "history",
        location_label: "Asbury Park boardwalk",
        duration_seconds: 74,
        signed_audio_url: "mock://clip/asbury-boardwalk-01",
      },
    ],
  });
});

app.post("/clips/upload-url", async (c) => {
  const body = await c.req.json().catch(() => ({}));

  return c.json(
    {
      clip_id: crypto.randomUUID(),
      upload_url: "mock://signed-r2-put-url",
      r2_path: `raw/${crypto.randomUUID()}-${body.filename ?? "clip.m4a"}`,
      expires_in_seconds: 900,
    },
    201,
  );
});

app.post("/clips", async (c) => {
  const body = await c.req.json();

  return c.json(
    {
      id: body.clip_id ?? crypto.randomUUID(),
      status: "pending_review",
      next_step: "Normalize audio to -16 LUFS, then manual review before publishing.",
    },
    202,
  );
});

app.post("/clips/:id/events", async (c) => {
  const clipId = c.req.param("id");
  await c.req.json().catch(() => ({}));

  return c.json({ accepted: true, clip_id: clipId }, 202);
});

export default app;
