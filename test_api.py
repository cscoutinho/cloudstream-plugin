#!/usr/bin/env python3
"""
FrameXTV Scraper Validation Script
Tests all REST endpoints, anime GraphQL, and multi-provider stream sources used by the CloudStream 3 provider.
"""

import urllib.request
import urllib.parse
import json
import sys
import time

API_BASE = "https://api.framextv.tech"
ANIME_API_BASE = "https://api.framextv.tech/anime"
ANILIST_API = "https://graphql.anilist.co"

DEFAULT_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://framextv.xyz/",
    "Origin": "https://framextv.xyz"
}

def http_get(url, headers=DEFAULT_HEADERS, timeout=10, retries=3):
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=timeout) as res:
                return res.read().decode('utf-8', errors='ignore')
        except urllib.error.HTTPError as e:
            if e.code in [502, 520, 521, 522, 524, 525] and attempt < retries - 1:
                time.sleep(1.5)
                continue
            raise
        except Exception as e:
            if attempt < retries - 1:
                time.sleep(1.5)
                continue
            raise

def test_home():
    print("[1/7] Testing Home Rails (/api/home)...")
    data = json.loads(http_get(f"{API_BASE}/api/home"))
    rails = data.get("rails", {})
    assert len(rails) >= 8, f"Expected at least 8 rails, found {len(rails)}"
    print(f" -> OK: Found {len(rails)} rails: {', '.join(list(rails.keys()))}")
    
    # Verify both movies/tv and anime rails exist
    assert "trending_movies" in rails, "Missing trending_movies rail"
    assert "popular_anime" in rails, "Missing popular_anime rail"
    
    anime_items = rails["popular_anime"].get("results", [])
    assert len(anime_items) > 0, "No anime items in popular_anime rail"
    print(f" -> OK: Anime rail populated with {len(anime_items)} titles. First: '{anime_items[0].get('title')}'")

def test_search():
    print("[2/7] Testing Search URL encoding (/api/search)...")
    # Multi-word query to verify URL encoding integrity
    query = "Fight Club"
    encoded_q = urllib.parse.quote(query)
    data = json.loads(http_get(f"{API_BASE}/api/search?q={encoded_q}"))
    results = data.get("results", [])
    assert len(results) > 0, f"No results for query '{query}'"
    first = results[0]
    first_title = first.get("title") or first.get("name")
    assert "Fight Club" in first_title, f"Unexpected top search result: '{first_title}'"
    print(f" -> OK: Query '{query}' correctly matched '{first_title}' (ID: {first.get('id')})")

def test_details():
    print("[3/7] Testing VOD Details (/api/details & /api/season)...")
    # Movie: Fight Club (550)
    m_data = json.loads(http_get(f"{API_BASE}/api/details?id=550&type=movie"))
    assert m_data.get("title") == "Fight Club", "Movie details mismatch"
    print(f" -> OK: Movie: '{m_data.get('title')}' ({m_data.get('year')}) Genres: {m_data.get('genres')}")

    # TV: Game of Thrones (1399)
    tv_data = json.loads(http_get(f"{API_BASE}/api/details?id=1399&type=tv"))
    assert "Game of Thrones" in tv_data.get("title", ""), "TV details mismatch"
    seasons = tv_data.get("seasons", [])
    print(f" -> OK: TV: '{tv_data.get('title')}' with {len(seasons)} seasons.")

    # TV Season 1 episodes
    s1_data = json.loads(http_get(f"{API_BASE}/api/season?id=1399&season=1"))
    episodes = s1_data.get("episodes", [])
    assert len(episodes) > 0, "No episodes returned for Season 1"
    print(f" -> OK: Season 1 has {len(episodes)} episodes. Ep 1: '{episodes[0].get('name')}'")

def test_anime_support():
    print("[4/7] Testing Anime Metadata & Stream Resolution...")
    # AniList GraphQL query for Attack on Titan (16498)
    query = """
    query ($id: Int) {
      Media(id: $id, type: ANIME, isAdult: false) {
        id
        title { english romaji native }
        episodes
        seasonYear
      }
    }
    """
    req = urllib.request.Request(
        ANILIST_API,
        data=json.dumps({"query": query, "variables": {"id": 16498}}).encode("utf-8"),
        headers={"Content-Type": "application/json", "User-Agent": DEFAULT_HEADERS["User-Agent"]}
    )
    with urllib.request.urlopen(req, timeout=10) as r:
        ani_data = json.loads(r.read().decode())
        media = ani_data["data"]["Media"]
        assert media["id"] == 16498, "AniList ID mismatch"
        print(f" -> OK: AniList Anime: '{media['title']['english']}' ({media['seasonYear']}), {media['episodes']} eps.")

    # Anime Stream Endpoint
    stream_data = json.loads(http_get(f"{ANIME_API_BASE}/api/stream?id=16498&type=anime&episode=1"))
    assert stream_data.get("success"), "Anime stream resolution failed"
    sources = stream_data.get("sources", [])
    assert len(sources) > 0, "No anime sources returned"
    first_src = sources[0]
    print(f" -> OK: Anime Server: '{first_src.get('server')}' | Quality: {first_src.get('quality')} | Type: {first_src.get('type')}")
    print(f"    Subtitles attached: {len(first_src.get('subtitles', []))} tracks.")

def test_vod_streams():
    print("[5/7] Testing VOD Stream Resolution (/api/stream)...")
    # Default stream (Headhunter / VidLink)
    s_data = json.loads(http_get(f"{API_BASE}/api/stream?id=550&type=movie"))
    assert s_data.get("success"), "Default stream resolution failed"
    print(f" -> OK: Default provider '{s_data.get('provider')}' with {len(s_data.get('sources', []))} sources.")

    # Bowler XVIII (PrimeVids & Citadel HLS)
    b_data = json.loads(http_get(f"{API_BASE}/api/stream?id=550&type=movie&provider=bowler"))
    assert b_data.get("success"), "Bowler stream resolution failed"
    b_sources = b_data.get("sources", [])
    assert len(b_sources) > 0, "No sources returned from Bowler"
    print(f" -> OK: Bowler XVIII returned {len(b_sources)} active HLS sources.")
    for s in b_sources[:2]:
        print(f"    - Server: {s.get('server')} | Quality: {s.get('quality')} | Type: {s.get('type')}")

def test_subtitles():
    print("[6/7] Testing Subtitles (/api/subtitles)...")
    sub_data = json.loads(http_get(f"{API_BASE}/api/subtitles?id=550&type=movie"))
    subs = sub_data.get("subtitles", [])
    assert len(subs) > 0, "No subtitles returned"
    languages = set(s.get("lang") or s.get("language") for s in subs if s.get("lang") or s.get("language"))
    print(f" -> OK: Found {len(subs)} subtitle tracks across languages: {', '.join(sorted(list(languages))[:8])}...")

def test_providers_catalog():
    print("[7/7] Testing Providers Catalog (/api/providers)...")
    p_data = json.loads(http_get(f"{API_BASE}/api/providers"))
    providers = p_data.get("providers", [])
    assert len(providers) > 0, "No providers returned"
    names = [p.get("name") for p in providers]
    print(f" -> OK: {len(providers)} registered providers: {', '.join(names[:4])}...")

def main():
    print("=" * 65)
    print("FrameXTV Full Scraper & Provider Verification Suite")
    print("=" * 65)
    try:
        test_home()
        test_search()
        test_details()
        test_anime_support()
        test_vod_streams()
        test_subtitles()
        test_providers_catalog()
        print("=" * 65)
        print("ALL 7 VERIFICATION PHASES PASSED! Backend is 100% operational.")
        print("=" * 65)
    except Exception as e:
        print(f"FAIL: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
