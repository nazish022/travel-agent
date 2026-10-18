# Travel Agent (Spring AI)

A chat-based AI travel agent built with Spring Boot and Spring AI. You talk to it in a web chat; it decides which Java "tools" to call (live flight, hotel and weather APIs, plus demo order lookups) and answers in plain language.

Started from the course project [LearnCodeWithDurgesh/ai-agents-using-spring-ai](https://github.com/LearnCodeWithDurgesh/ai-agents-using-spring-ai/tree/main/ai-agent-backend), which uses hard-coded flights, hotels and weather. This version replaces those with live APIs.

**Status:** Flights ✅ live · Hotels ✅ live · Weather ✅ live · Trip planner ✅ · Orders & inventory 🧪 dummy data

**Goal:** a balanced trip plan: cheap flights, a stay that fits the budget, and the weather for each day.

---

## How it works

```
Browser chat (index.html)
   │  POST /chat   body: message   header: Conversation-Id
   ▼
ChatController ─► ChatService ─► ChatClient (Gemini via OpenAI-compatible API)
                                     │  remembers last 10 messages per Conversation-Id
                                     │  model picks tools to call
                                     ▼
        FlightTools · HotelTools · WeatherTools · OrderTools · InventoryTools
             │             │            │
             │             │            └─ Open-Meteo              (forecast, free, no key)
             │             └─ SerpApi Google Hotels                (hotel search + price per site)
             ├─ SerpApi Google Flights                             (flight search + price per site)
             └─ AviationStack                                      (live flight status)
```

1. The page sends your message plus a `Conversation-Id` (a random ID kept in the browser until you click **New chat**).
2. `ChatClient` sends it to the model with the system prompt, chat memory and the list of tools.
3. The model calls whichever tools it needs, reads their results and writes the reply.
4. The page renders the reply as Markdown (tables, lists, links), sanitized with DOMPurify.

## Tech stack

| Part | Choice |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring AI 2.0.1 |
| Model | Gemini (`gemini-3.1-flash-lite`) through its OpenAI-compatible endpoint |
| Frontend | Thymeleaf, Tailwind CSS + typography plugin (CDN), Marked, DOMPurify, plain JavaScript |
| HTTP calls | Spring `RestClient`, Jackson 3 |

## Project structure

```
travel-agent/
├── README.md
├── .gitignore                         # .env, .vscode, target, logs
├── docs/prompt.md                     # test prompts with expected results and quota cost
└── ai-agent-backend/                  # Spring Boot app (run everything from here)
    ├── .env.example                   # copy to .env and add your keys
    ├── pom.xml
    └── src/main/
        ├── java/com/substring/agent/backend/
        │   ├── ai_agent_backend/AiAgentBackendApplication.java   # entry point
        │   ├── config/AiConfig.java              # ChatClient: system prompt, memory, tools
        │   ├── config/ApiLogging.java            # RestClient for external APIs: timeout, call logging, key masking
        │   ├── config/LoggingToolCallback.java   # logs each tool call, result and duration
        │   ├── controller/ChatController.java    # POST /chat
        │   ├── controller/PageController.java    # GET / → chat page
        │   ├── service/ChatService.java          # calls ChatClient with the conversation ID
        │   └── tools/
        │       ├── FlightTools.java              # live: search, compare sites, flight status
        │       ├── HotelTools.java               # live: search, compare sites
        │       ├── WeatherTools.java             # live: daily forecast
        │       ├── OrderTools.java               # dummy order data
        │       └── InventoryTools.java           # dummy stock data
        └── resources/
            ├── application.yml                   # port 8081, model, logging, loads .env
            └── templates/index.html              # chat UI
```

## Setup

1. **Java 21** installed.
2. Clone and go into the app folder; all commands below run from here:
   ```bash
   git clone https://github.com/nazish022/travel-agent.git
   cd travel-agent/ai-agent-backend
   ```
3. Copy the example env file and fill in your keys (`.env` is git-ignored):
   ```bash
   cp .env.example .env
   ```
   | Key | Get it at | Free tier |
   |---|---|---|
   | `GEMINI_API_KEY` | https://aistudio.google.com/apikey | yes |
   | `SERPAPI_API_KEY` | https://serpapi.com/users/sign_up | 250 searches/month (flights + hotels) |
   | `AVIATIONSTACK_API_KEY` | https://aviationstack.com/signup/free | ~100 requests/month, HTTP only |

   Weather (Open-Meteo) needs no key. `CHAT_MODEL` picks the Gemini model (default `gemini-3.1-flash-lite`); change it in `.env` to try another model without touching code. Don't leave it empty.
4. Run (from `ai-agent-backend/`, since `.env` is read from the current folder):
   ```bash
   ./mvnw spring-boot:run
   ```
5. Open http://localhost:8081/

## Logging

Logs go to the console and to `ai-agent-backend/logs/app.log` (git-ignored; Spring Boot rotates it at 10 MB).

| What | Level | Example |
|---|---|---|
| Every tool call: arguments, duration, result (first 500 chars) | INFO | `Tool getWeather called with {"placeName":"Goa",...}` / `Tool getWeather returned in 890 ms, 155 chars: ...` |
| Every external API call: URL with keys masked, status, time, size | INFO | `API GET https://serpapi.com/search.json?...&api_key=*** -> 200 in 3027 ms, 167708 bytes` |
| Full tool result and raw API response body (first 5000 chars) | DEBUG | `API response body: {...}` |
| Every request to and response from the model | DEBUG on `SimpleLoggerAdvisor` | off by default: very verbose |

Set levels in `application.yml` under `logging.level`: `com.substring.agent.backend` (tools and APIs, DEBUG by default) and `org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor` (model traffic, INFO = off).

Handy: `tail -f logs/app.log | grep -E "Tool |API "` shows just the tool and API lines as you chat.

API keys are never logged: `ApiLogging.mask` replaces `api_key` / `access_key` values with `***` (covered by `ApiLoggingTest`).

## Testing

- `./mvnw test` (from `ai-agent-backend/`): checks the app starts (`contextLoads`) and that API keys are masked in logs (`ApiLoggingTest`).
- [`docs/prompt.md`](docs/prompt.md): prompts to try by hand, with the expected result and SerpApi cost of each.

---

## Features

### ✈️ Flights (live)

| Tool | API | What it does |
|---|---|---|
| `searchFlights` | SerpApi Google Flights | One-way search for N adults in any cabin class; returns top 5 options with total price (INR), duration, stops, flight numbers, a booking token, and Google's price insights (low / typical / high) |
| `getBookingOptions` | SerpApi Google Flights | For one option, lists every airline/travel agent selling it (Booking.com, MakeMyTrip, airline site…), cheapest first, with a booking link each |
| `getFlightStatus` | AviationStack | Live status by flight number: scheduled/active/landed, delay, terminal, gate |

**Search filters** the agent fills in from your wording:

| You say | Filter |
|---|---|
| "cheapest" | sort by price (default) |
| "morning / afternoon / evening / night" | departure hours 5–11 / 12–16 / 17–20 / 21–23 |
| "earliest" | sort by departure time |
| "fastest" | sort by duration |
| "nonstop", "direct" | nonstop only |
| "for 2 adults" | adults = 2 |
| "business class" | cabin class: economy, premium economy, business, first |

**Round trips** are two one-way searches (outbound, then return with airports swapped), each with its own booking options; the agent adds up the total. Trade-off: round-trip-only fares Google sometimes offers aren't seen.

**Dates:** today's date is added to the system prompt on every request, so "tomorrow" or "next Friday" work.

**Try:**
- `cheapest flight from DEL to BOM on 2026-11-20`
- `morning nonstop flights from DEL to BOM on 2026-11-20`
- `round trip DEL to BOM for 2 adults in business, leaving next Friday, back Sunday. Is it a good price?`
- `status of flight AI101`

**Limits:**
- No real booking: no public API lets individuals issue tickets. The agent gives the cheapest site and a link instead.
- Adults only (no children/infants). Prices in INR.
- Each search or site comparison costs 1 SerpApi search, so a round trip with links costs 4.

### 🏨 Hotels (live)

| Tool | API | What it does |
|---|---|---|
| `searchHotels` | SerpApi Google Hotels | Hotels for a city, dates and number of adults; top 5 with price per night and total stay (INR), rating, star class, coordinates and a property token |
| `getHotelBookingOptions` | SerpApi Google Hotels | Prices for one hotel on each site (Agoda, Booking.com, hotel website…), cheapest first, with booking links |

Filters: max price per night, sort by price / rating / reviews, minimum rating (3.5, 4, 4.5).

**Try:**
- `best rated hotels in Jaipur under ₹4000 a night for next weekend`
- `where can I book the first one?`

**Limits:** no real booking (cheapest site and link instead); default 2 adults if not given; each search or comparison costs 1 SerpApi search.

### 🌦️ Weather (live)

| Tool | API | What it does |
|---|---|---|
| `getWeather` | Open-Meteo (free, no key) | Daily conditions, min/max °C, rain (mm) and rain chance for a date range |

- Takes latitude/longitude (the model knows them, and hotel results include them). Open-Meteo's name lookup turns "Goa" into Genoa, Italy, so it isn't used.
- Real forecasts only go 16 days ahead. Later dates get the same dates last year as a guide, and the reply says so.

### 🧳 Trip planner

Ask for a trip with a budget and the agent:
1. Finds the cheapest flights (both ways) and the cheapest site for each.
2. Uses the rest of the budget for the stay: searches hotels for the trip dates, preferring 4+ ratings.
3. Compares booking sites for the chosen hotel.
4. Gets the weather for each day and adds a tip.

It replies with a summary, a cost table (flights, hotel, total vs budget) with booking links, and the daily weather. If nothing fits, it says so.

**Try:** `Plan a trip from Mumbai to Goa for 2 adults, Oct 30 to Nov 1. Total budget for flights + hotel is ₹40000.`

A full plan takes about 6 SerpApi searches and about a minute.

### 💬 Chat page

- Replies rendered as Markdown (tables, headings, lists); links open in a new tab. Sanitized with DOMPurify so API content can't inject HTML.
- Suggestion buttons on the empty screen; click one to send it.
- **New chat** clears the screen and history and starts a new conversation ID.
- History is saved in the browser (`localStorage`) and survives reloads. Works without it if storage is blocked.
- Dark mode toggle, Enter to send, Shift+Enter for a new line.

Note: the server's chat memory lives in memory, so after an app restart the agent forgets messages still shown on the page. Click **New chat** to start clean.

### 📦 Orders & inventory (dummy data)

`OrderTools` (`getOrderStatus`, `cancelOrder`, `getOrderCount`) and `InventoryTools` (`checkStock`, `getTotalProductsInStock`, `getAllProductsInStock`) use hard-coded sample data, e.g. order IDs `1042`–`1045`.

---

## Roadmap

- [x] Add today's date to the system prompt (so "tomorrow" / "next Friday" work)
- [x] Round trip (as two one-way searches), number of passengers, cabin class
- [x] Show Google's price insights ("is this a good price?")
- [x] Prices in INR
- [x] Restore "recommend the cheapest site with link" instructions in the system prompt
- [x] Hotels
- [x] Weather
- [x] Trip planner: flight + hotel + weather within a budget
- [x] Chat page: Markdown, suggestion buttons, New chat, saved history
- [ ] Tune the planner to use the remaining budget for a better hotel instead of the cheapest 4+ one
- [ ] One booking link per flight leg in the trip plan table (sometimes merged)
- [ ] Return only the 5 cheapest sellers from `getBookingOptions` (all 17 sellers ≈ 12k tokens per call; slows trip plans)
- [ ] Friendly error message when Gemini fails, instead of "Server error 500"
- [ ] Same default number of adults for flights (1) and hotels (2)
- [ ] Add `@Bean` to `chatMemory()` in `AiConfig`
- [ ] Clear old conversations from the in-memory chat memory
- [ ] Fix or remove the top-level `.vscode/launch.json` (starts from `travel-agent/`, where there is no `.env`)
- [ ] Put the project under git (`git init`)
- [ ] Cache recent searches to save SerpApi quota

**Possibly unused, kept until decided:** `mysql-connector-j` dependency, `model/Flight.java`, `model/Hotel.java`, `model/User.java`, Lombok (only used by `Flight.java`), `application.properties` (duplicates `application.yml`), `HELP.md`.

## Changelog

### 2026-10-17
- Chat UI (Thymeleaf + Tailwind); `/chat` switched to POST.
- Live flight search, per-site price comparison and flight status (SerpApi Google Flights, AviationStack).
- Flight filters: time of day, nonstop, sort order. Chat memory per conversation.
- Today's date in the system prompt; round trips, passengers, cabin class; price insights.
- Fixed chat page failing to render (Thymeleaf inlining).
- Live hotels (SerpApi Google Hotels) and weather (Open-Meteo); trip-planning instructions; all prices in INR.
- 60s read timeout for API calls (fixed booking-link failures); agent may only share links returned by tools.
- Chat page: Markdown (Marked + DOMPurify), suggestion buttons, New chat, history saved in the browser.
- Added root `.gitignore` and `docs/prompt.md`.
- Full project review against the course repo; open items added to the roadmap.

### 2026-10-18
- Logging: every tool call (args, result, duration) and API call (masked URL, status, time, body at DEBUG), written to console and `logs/app.log`. Optional model request/response logging. `ApiLoggingTest` for key masking.
- `.env.example` added; `CHAT_MODEL` read from `.env`.
