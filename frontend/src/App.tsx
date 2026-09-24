import { Check, Copy, Link2, Loader2 } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ShortenError, shortenUrl } from "@/lib/api"

function App() {
  const [url, setUrl] = useState("")
  const [shortUrl, setShortUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [copied, setCopied] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!url.trim() || isSubmitting) return

    setIsSubmitting(true)
    setError(null)
    setShortUrl(null)
    setCopied(false)

    try {
      const result = await shortenUrl(url.trim())
      setShortUrl(result.shortURL)
    } catch (err) {
      setError(err instanceof ShortenError ? err.message : "Something went wrong. Please try again.")
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleCopy() {
    if (!shortUrl) return
    try {
      await navigator.clipboard.writeText(shortUrl)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // Clipboard access can fail (permissions, insecure context); the
      // short URL is still selectable/visible, so there's nothing to recover.
    }
  }

  return (
    <main className="flex min-h-svh flex-col items-center justify-center gap-8 px-4">
      <div className="flex flex-col items-center gap-2 text-center">
        <div className="flex items-center gap-2 text-foreground">
          <Link2 className="size-6" />
          <h1 className="text-2xl font-semibold">Dinky Link</h1>
        </div>
        <p className="text-muted-foreground">Shorten a long URL into something tweet-sized.</p>
      </div>

      <form onSubmit={handleSubmit} className="flex w-full max-w-md flex-col gap-3">
        <div className="flex gap-2">
          <Input
            type="url"
            inputMode="url"
            placeholder="https://example.com/a-very-long-url"
            value={url}
            onChange={(event) => setUrl(event.target.value)}
            aria-label="Long URL"
            aria-invalid={error != null}
            required
          />
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? <Loader2 className="animate-spin" /> : null}
            Shorten
          </Button>
        </div>
        {error ? <p className="text-sm text-destructive">{error}</p> : null}
      </form>

      {shortUrl ? (
        <div className="flex w-full max-w-md items-center gap-2 rounded-lg border border-border bg-card p-3">
          <a
            href={shortUrl}
            target="_blank"
            rel="noreferrer"
            className="flex-1 truncate text-sm font-medium text-primary underline-offset-4 hover:underline"
          >
            {shortUrl}
          </a>
          <Button type="button" variant="outline" size="icon" onClick={handleCopy} aria-label="Copy short URL">
            {copied ? <Check className="text-primary" /> : <Copy />}
          </Button>
        </div>
      ) : null}
    </main>
  )
}

export default App
