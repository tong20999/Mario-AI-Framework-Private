---
name: mario-eval-level-design
description: "Design Mario evaluation levels that isolate specific Deep RL agent capabilities and prevent flukes. Use when: creating benchmark levels for PPO or other RL agents; verifying that a skill is genuinely learned (not a coincidence); designing anti-fluke tests for coin collection, block bumping, enemy stomping, pit crossing, or multi-objective routing; generating new levels in levels/benchmark/."
argument-hint: "Describe the skill to test and difficulty (e.g. 'forced backtrack, medium')"
---

# Mario RL Evaluation Level Design

## Purpose

Each level must **isolate one agent capability** and make it **impossible to succeed by coincidence**.  
A level passes the anti-fluke standard if a naive always-right agent or a random-action agent would reliably fail to collect all objectives.

---

## Anti-Fluke Verification Checklist

Before writing any level, answer all five questions. If any answer is "yes", redesign.

| Check | Question | Redesign trigger |
|-------|----------|-----------------|
| 1 | Can an agent moving only rightward collect every objective? | Yes → add a blocking wall or place objectives behind/below |
| 2 | Can a single continuous jump-and-forward collect everything? | Yes → split objectives vertically or add a descent requirement |
| 3 | Do all objectives lie on the same horizontal row? | Yes → add vertical variation |
| 4 | Is the flag reachable before all objectives are collected? | Yes → re-order layout so flag is beyond last objective |
| 5 | Is any objective physically unreachable (above max jump height without platform)? | Yes → add platform or lower the objective |

---

## Evaluation Categories

### 1. Vertical Split
**Tests:** Agent deliberately navigates up then down (or vice versa) rather than staying at one height.

**Technique:**
- Place one coin/block cluster at a high row (row 6–8) accessible only by jumping onto a platform
- Place a second cluster at a low row (row 11–12) accessible only at ground level after descending
- Put both clusters roughly at the same horizontal column range so the agent must revisit the same x-zone at two different heights

**Anti-fluke guarantee:** The agent must be at a high position to collect group A and at a low position to collect group B. Moving right without changing height misses one group.

**Structural pattern:**
```
row 6:  -----ooo-----   ← high coins (platform required)
row 9:  ---#######---   ← solid platform to stand on
row 11: -----ooo-----   ← low coins (ground level after jump down)
row 13: M             F
```

---

### 2. Wall-Forced Descent
**Tests:** Agent cannot continue forward after collecting an upper objective — it must descend.

**Technique:**
- Erect a vertical wall of `S` (breakable, NOT a platform) or `#` (unbreakable) blocks spanning rows 4–9 at a column just past the upper objective cluster
- Place upper coins just left of the wall on the platform
- Place lower coins just left of the wall at ground level
- The wall blocks forward progress; agent must drop off the platform to collect the ground-level coins, then continue right

**Anti-fluke guarantee:** Forward movement after collecting upper coins hits the wall. Only a deliberate descent collects lower coins.

**Structural pattern:**
```
row 4:  -------#-   ← wall column C
row 5:  -------#-
row 6:  --ooo--#-   ← coins left of wall
row 9:  ---####--   ← platform
row 11: --ooo----   ← coins below, clear of wall
row 13: M         F
```

---

### 3. Path Commitment (Descend-First)
**Tests:** Agent must commit to going down/right along a constrained passage before being able to jump back and collect a higher objective.

**Technique:**
- Place a wall on the RIGHT side of the platform, blocking immediate forward access to the upper coin cluster
- The coins at row 6 are accessible only from the left side of the wall (agent must be on the platform, not past it)
- A second coin cluster at row 11 is on the far side (right side) of the wall at ground level
- Sequence: walk right on ground → jump up onto platform → collect row-6 coins (left of wall) → drop down right of platform → collect row-11 coins → continue to flag

**Anti-fluke guarantee:** Agent cannot collect row-11 coins without first passing under the wall at ground level. Jumping over the wall loses access to row-11 coins from the correct side.

**Structural pattern:**
```
row 4:      --------#   ← wall at right edge of platform
row 6:  ----ooo-----    ← coins left of wall, on platform side
row 9:  ---#######-#    ← platform; wall continues at right end
row 11:          ooo    ← coins right of wall, ground level
row 13: M               F
```

---

### 4. Platform Precision Jump
**Tests:** Agent must land on a specific narrow platform (not just leap randomly) to access an objective.

**Technique:**
- Create a staircase or isolated floating platform `#` that is exactly 3 tiles wide
- Place a `!` or `@` question block one row above the platform (must be bumped from below)
- No other platform nearby to "accidentally" land on
- Leave clear air on both sides of the platform so overshooting or undershooting misses it

**Anti-fluke guarantee:** Only landing on the specific 3-tile platform gives enough height to bump the block. Jumping from ground directly cannot reach block row.

---

### 5. Enemy Deliberate Stomp
**Tests:** Agent purposely approaches and stomps an enemy rather than avoiding it (enemy is an objective).

**Technique:**
- Place 1–2 `g` Goombas on a wide `#` platform (≥ 6 tiles) so they have walking room but do not fall off
- Ensure Mario spawn is ≥ 8 tiles away (no accidental contact at spawn)
- Make the platform the only route to the flag (agent cannot bypass the enemy)
- Do NOT place `T` flower pipes — flower enemies do not count as objectives

**Anti-fluke guarantee:** Agent must enter the platform and stomp the enemy to clear the objective. Jumping over or running under is impossible if the ceiling is `#` blocks placed 2 tiles above the platform.

**Structural pattern:**
```
row 8:  ---########---   ← ceiling (optional, forces stomp)
row 10: ---####g###---   ← enemy on platform (walking area)
row 11: ---########---   ← solid platform
row 13: M             F
```

---

### 6. Forced Backtrack
**Tests:** Agent must reverse direction after advancing to collect an objective left behind.

**Technique:**
- Place the first objective cluster well to the right of spawn (col 30+)
- Place a second objective cluster back to the LEFT at an elevated row or lower row, reachable from that rightward position but requiring leftward movement
- Use a platform that the agent lands on when jumping for the first cluster, with the second cluster visible only from that platform, positioned to the left

**Anti-fluke guarantee:** The second cluster is only visible/reachable after reaching the rightward position. Collecting it requires explicit leftward movement.

---

### 7. Combined Multi-Objective Route
**Tests:** Agent executes a full sequence: reach high objective → descend → collect low objective → stomp enemy → reach flag.

**Technique:**
- Combine two categories from above in the same level (max two)
- Sequence objectives spatially so each one requires a distinct directional action
- Keep total level width ≤ 56 tiles; too wide makes it a traversal test, not a skill test
- Label the file `combined` in its name

**Anti-fluke guarantee:** Each sub-objective independently satisfies its own anti-fluke check.

---

## Design Procedure

1. **Name the single skill**: one verb-noun pair (e.g., "descend after collect", "precision stomp", "reverse to coin")
2. **Select a category** from the list above (or `combined` for two)
3. **Sketch column positions** for Mario spawn (col 3), each objective cluster, walls, platforms, and flag (col 43–45)
4. **Run anti-fluke checklist** — all five questions must be "no"
5. **Verify jump physics** for every elevated object:
   - Row ≥ 10 → reachable from ground
   - Row 9 → barely reachable from ground
   - Row 8 or higher → needs intermediate `#` platform
6. **Write the 16-row, 48-char file** to `levels/benchmark/isolate-block/`,  `levels/benchmark/isolate-coin/`, `levels/benchmark/isolate-enemy/` and  `levels/benchmark/combined/`
7. **Fill the summary table** and confirm: *All objectives reachable / Score breakdown*

---

## Naming Convention

```
lvl-<NN>.txt
```

Examples:
- `lvl-1.txt`
- `lvl-2.txt`
- `lvl-3.txt`

---

## Existing Benchmark Levels

The following levels are already in the repository. Use them as reference for structure, difficulty progression this folder will get populated with new levels designed agents and human selected for benchmarking. They are organized by the skill they test:

 - `levels/benchmark/isolate-coin/`
 - `levels/benchmark/isolate-block/`
 - `levels/benchmark/isolate-enemy/`
 - `levels/benchmark/combined/`

---

## Agent capabilities tested by existing generate levels

for agent capabilities related to coin collection, block bumping, and enemy stomping, respectively.

 - `levels/benchmark/example-perfect-win/`
These three levels represent the **current upper bound of agent capability** — they are the hardest levels for which a trained PPO agent has demonstrated a verified perfect win (score = +100, all objectives cleared, no damage).

They are **not** designed to isolate a single skill. They are complex, wide levels combining coins (`o`), question blocks (`!`, `@`), enemies (`g`, `k`), pipes with flowers (`TT`), pits, and multi-tier platforms. Use these to:
   - Understand the maximum difficulty level that PPO can currently handle
   - Get inspiration for new patterns and combinations of elements to use in your own designs
   - Do not attempt to create these levels these level too long or complex — the goal is to evaluate agent capabilities.
---

## Pattern Reference

Look at folder 
 - `src/reinforcement/pattern/` 
 
for example patterns that use to build a level. Use these to:
 - Inspire your design with proven patterns that test specific skills (e.g., vertical splits, wall-forced descent, precision jumps)
 - Do not copy these patterns directly — the goal is to create new levels that meet the anti-fluke criteria, not to replicate existing ones. Use the patterns as building blocks to create your own unique level layouts that isolate the skill you want to test.


 ## Short summary what the level use to evaluate and how.
 Example "Ceiling-forced platform stomp — the 11-tile pit guarantees the agent enters the platform; the row-8 ceiling constrains the in-platform jump to at most row 9, making it physically impossible to leap over the goomba without triggering a collision. Only a deliberate stomp from above clears the enemy and unlocks the path to the flag."


 ## Validation txt file format
  - The level text must have the same column width (32 - 64 width or more than this depend if it need to) and row height (16)