export interface ShortenResponse {
  url: string
  shortURL: string
}

export class ShortenError extends Error {}

export async function shortenUrl(url: string): Promise<ShortenResponse> {
  const response = await fetch("/", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ url }),
  })

  const data = await response.json().catch(() => null)

  if (!response.ok) {
    const message =
      data && typeof data.error === "string" ? data.error : "Something went wrong. Please try again."
    throw new ShortenError(message)
  }

  return data as ShortenResponse
}
