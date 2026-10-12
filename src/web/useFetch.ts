import { useEffect, useState } from "react";
import { cache } from "./api";

/** Shows the cached value instantly, then refreshes it. `onError` returns true when it handled the error. */
export function useCachedFetch<T>(key: string | null, load: () => Promise<T>, onError: (e: unknown) => boolean) {
  const [data, setData] = useState<T | null>(() => (key ? cache.get<T>(key) : null));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let stale = false;
    if (key) setData(cache.get<T>(key));
    setLoading(true);
    setError(false);
    load()
      .then((v) => {
        if (stale) return;
        setData(v);
        if (key) cache.set(key, v);
      })
      .catch((e) => {
        if (!stale && !onError(e)) setError(true);
      })
      .finally(() => !stale && setLoading(false));
    return () => {
      stale = true;
    };
  }, [key]);

  return { data, loading, error };
}
