# 0006. App icons come from an image model, only when the person asks

Date: 2026-10-20. Status: accepted (the real image request is untested until the
user approves one).

The user wants a generated app to carry the name the person gave it, or a short
name the builder picks and tells them, and an icon made by image generation
when the person asks for one (spec #59, items 4 and 5).

The name needs no new mechanism. The model already owns the app's
`AndroidManifest.xml` (ADR 0004), and `AppManifestPolicy` neither forces nor
checks `android:label` or `android:icon`. The build contract now tells the
builder to set `android:label` on `<application>` to the person's name, or,
while it is still the default "Smallwright App", to a short fitting name it
tells the person about.

The icon is a new kind of paid request from the controller. The run offers a
`make_app_icon(description)` tool, and the contract and the tool's description
say to call it only when the person asks for an icon or a logo. The controller
then sends one OpenRouter chat completion to `google/gemini-3.1-flash-lite-image`
with `modalities: ["image", "text"]` and `image_config.aspect_ratio` "1:1",
with the person's own key, exactly as the run's model requests are sent. The
model's description is the only project-derived text in it. The tap on Send
approves this request like every other request of the run (ADR 0003): its
reported cost counts in the run's requests and spending, and an icon is not
asked for once the person's spending limit is reached. A run asks for at most
one icon, so a reply that cannot be read or counted is never repeated.

The reply's first image must be a base64 `data:` URL of a PNG, JPEG or WebP
whose first bytes match that type (on 2026-10-25 the model began sending
non-PNG images). Before any decoder sees it, the controller checks its size (at
most 4 MB, reply at most 6 MB) and, for a PNG, the header's width and height.
Android's own decoder reads every image's width and height first and refuses
anything outside 64 to 4096 pixels, then reads it with a sample size, crops the
centred square and writes a 288-pixel PNG. The icon is an adaptive icon,
`res/mipmap-anydpi-v26/app_icon.xml`: a white background and the picture
inset by a sixth on each side as the foreground, so it fills the part the
launcher shows and the launcher gives it its shape. The picture is
`res/drawable-nodpi/app_icon_image.png`. The manifest gets
`android:icon` and `android:roundIcon` = `@mipmap/app_icon`, and so does the
launcher activity's own icon if it has one. These are ordinary project files,
so the worker compiles them like any other resource; it needs no change.
Every failure (no image, not a PNG, JPEG or WebP, too small, an HTTP error) is a plain tool
result, and the run goes on.

## Considered options

- One `res/mipmap-*/ic_launcher.png` per density, as a legacy icon. Rejected:
  launchers since Android 8 shrink a legacy icon onto a white tile. Generated
  apps start at API 31, where every launcher reads adaptive icons.
- The model drawing a vector icon itself. Still possible with the file tools,
  but the user asked for image generation.
- OpenRouter's separate `/api/v1/images` endpoint. The brief asked for the chat
  completions route that Gemini image models use; either reports the cost.

## Consequences

- Each icon is a paid request at the image model's price, outside the price
  the person sees for the model they picked.
- Untested against the real service: tests use a stand-in server and the phone
  run uses a fixed PNG file. The first real request needs the user's approval.
