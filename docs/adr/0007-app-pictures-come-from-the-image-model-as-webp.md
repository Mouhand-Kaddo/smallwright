# 0007. App pictures come from the image model, as WebP drawables

Date: 2026-10-25. Status: accepted (the real image request is untested until the
user approves one).

The user wants the builder to make the pictures an app shows (backgrounds,
illustrations, art for empty screens, photos of items) with OpenRouter's image
generation, like the app icon (ADR 0006).

The run offers `make_image(name, description, shape)`. It sends the same
request as `make_app_icon`: one OpenRouter chat completion to
`google/gemini-3.1-flash-lite-image` with `modalities: ["image", "text"]`, with
the person's key, and `image_config.aspect_ratio` from the shape: square
`1:1`, wide `16:9`, tall `9:16` (OpenRouter's normalized ratios, which
providers clamp to what they support). Its reported cost counts in the run's
requests and spending, a picture is not asked for once the spending limit is
reached, and every failure is a plain tool result. The contract and the tool's
description say to call it only when the person asked for pictures or agreed to
them in the interview.

The name is checked before anything is paid for: a lower-case resource name,
not a Java word, not the icon's `app_icon` or `app_icon_image`, and not the
name of another drawable file in the project. Making a name again replaces its
picture.

The reply's image gets the same checks as an icon's. Android's decoder then
shrinks it, keeping its shape, to at most 1536 pixels a side and writes it as a
lossy WebP (quality 85) at `res/drawable-nodpi/<name>.webp`. The tool result
gives `@drawable/<name>`, `R.drawable.<name>` and the size in pixels. A project
file may be at most 1 MB and a project 16 MB, and a PNG straight from the model
is often over 1 MB, so pictures are WebP. A picture still over 1 MB is refused.

There is no fixed number of pictures per run: the run's limits and Stop govern
spending. A request that fails without a reply to count, however, stops further
image requests in that run, because a spending limit cannot see a cost that was
never reported. The person can ask again in their next message.

## Considered options

- PNG as the brief first said. Rejected for size: most model pictures would
  exceed the 1 MB project file limit, and a few would fill the project.
- A fixed number of pictures per run. Rejected by the user; the spending limit
  and Stop do this job.
- Trying a failed image request again. Not done, as for icons: a retried
  request can be charged twice without the run knowing.

## Consequences

- Each picture is a paid request at the image model's price.
- Lossy WebP loses a little detail; the pictures are illustrations and photos,
  not text or line art.
- Untested against the real service: tests use a stand-in server and the phone
  probe uses a fixed PNG file.
