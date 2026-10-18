# Test prompts

Prompts for checking the agent by hand. Each SerpApi call counts against the free 250 searches a month, so the rough cost is noted.

## Trip planner (~6 searches, ~1 minute)

```
Plan a trip from Mumbai to Goa for 2 adults, Oct 30 to Nov 1. Total budget for flights + hotel is ₹40000.
```
Expect: cheapest flights both ways with booking links, a hotel within the remaining budget with a booking link, a cost table vs budget, weather per day with a tip.

```
Plan a trip from Delhi to Goa for 1 adult, leaving next Friday and returning Sunday. My total budget is ₹20000.
```
Expect: if over budget, the agent says so and suggests the closest option or other dates.

## Flights

```
cheapest flight from DEL to BOM on 2026-11-20                 (2 searches: search + site comparison)
morning nonstop flights from DEL to BOM on 2026-11-20          (1 search; filter hours 5-11, nonstop)
fastest flight from DEL to BOM on 2026-11-20 in the evening    (1 search; sort by duration, hours 17-20)
round trip DEL to BOM for 2 adults in business, leaving next Friday, back Sunday. Is it a good price?   (4 searches)
status of flight AI101                                         (1 AviationStack request)
```

## Hotels

```
best rated hotels in Jaipur under ₹4000 a night for next weekend   (1 search)
where can I book the first one?                                     (1 search: site comparison)
```

## Weather (free, no quota)

```
What's the weather in Goa this weekend?        (real forecast, up to 16 days ahead)
What will the weather be like in Manali on 2026-12-20?   (beyond 16 days: last year's weather on that date)
```

## Dates

```
What date is next Friday? Just the date.
```
Expect: the correct date (today's date is in the system prompt).
