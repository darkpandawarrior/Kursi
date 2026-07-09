#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  make_flow_gifs.sh — stitch ordered docs/screenshots/*.png frames into animated
#  flow GIFs with a subtle crossfade, a clean generated palette, and a fixed width.
#
#  Each GIF is one JOURNEY: a logical multi-screen flow (onboarding, a turn, the
#  coach, career, online, …). Prefer a few moving flows over dozens of stills.
#
#  Usage:  scripts/make_flow_gifs.sh              # build every flow defined below
#          scripts/make_flow_gifs.sh onboarding   # build one flow by name
#
#  Frames are read from docs/screenshots/<name>.png; GIFs written to docs/gifs/.
#  Tunables: DUR (per-frame seconds), XF (crossfade seconds), W (output width px).
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

FFMPEG=/opt/homebrew/bin/ffmpeg
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SHOTS="$ROOT/docs/screenshots"
OUT="$ROOT/docs/gifs"
mkdir -p "$OUT"

DUR=${DUR:-1.5}   # seconds each frame is fully shown (~1.5s/screen)
XF=${XF:-0.4}     # crossfade duration between frames
W=${W:-640}       # output width (landscape 1440x900 desktop renders → 640 keeps dense UI legible)
FPS=${FPS:-20}

# ── Flow definitions: name = ordered frame list ──────────────────────────────
# Every committed docs/screenshots/*.png belongs to exactly one journey.
declare -a FLOWS=(
  "onboarding   profile_setup tutorial_intro tutorial_bluff_caught home"
  "home         home home_mode_gauntlet home_mode_story home_ranked home_resume"
  "turn         4p_pick_action 4p_confirm 4p_reaction 4p_reaction_block 4p_exchange 4p_lose_influence 4p_game_over results"
  "coach        4p_coach_action 4p_chit_dossier 4p_chit_risk 4p_coach_reaction 4p_pick_action_nocoach 4p_mid_claim"
  "darbar       story darbar_table"
  "modes        setup setup_teams gauntlet team_table spectator_demo passandplay_handoff"
  "table_sizes  2p_pick_action 4p_pick_action 10p_pick_action"
  "career       results results_expired career leaderboard leaderboard_online review_replay review_recent_list"
  "online       online_hub online_hub_lan online_lobby online_lobby_lost"
  "reference    gazette_roles reduced_motion_frames settings"
)

build_flow() {
  local name=$1; shift
  local frames=("$@")
  local inputs=() filter="" n=${#frames[@]}

  for f in "${frames[@]}"; do
    local p="$SHOTS/$f.png"
    [[ -f "$p" ]] || { echo "  !! missing frame: $p (skipping flow '$name')"; return 1; }
    inputs+=(-loop 1 -t "$DUR" -i "$p")
  done

  # Scale + pad every input to a common WxH canvas so xfade inputs line up.
  local h; h=$(awk -v w="$W" 'BEGIN{printf "%d", (w*900/1440); }')
  h=$(( (h/2)*2 ))  # even height for encoders
  local labels=()
  for ((i=0;i<n;i++)); do
    filter+="[$i:v]scale=$W:$h:force_original_aspect_ratio=decrease,pad=$W:$h:(ow-iw)/2:(oh-ih)/2:color=0x1A1A2E,setsar=1,fps=$FPS[s$i];"
    labels+=("[s$i]")
  done

  if (( n == 1 )); then
    filter+="${labels[0]}copy[vout]"
  else
    # Chain xfade: transition j starts at offset j*(DUR-XF).
    local prev="${labels[0]}"
    for ((j=1;j<n;j++)); do
      local off; off=$(awk -v j="$j" -v d="$DUR" -v x="$XF" 'BEGIN{printf "%.3f", j*(d-x)}')
      local out="[x$j]"; (( j == n-1 )) && out="[vout]"
      filter+="${prev}${labels[$j]}xfade=transition=fade:duration=$XF:offset=$off$out;"
      prev="$out"
    done
    filter="${filter%;}"
  fi

  # Two-pass palette for clean GIF colours (palettegen → paletteuse).
  echo "  → $name.gif  (${n} frames)"
  $FFMPEG -hide_banner -loglevel error -y "${inputs[@]}" \
    -filter_complex "${filter};[vout]split[a][b];[a]palettegen=max_colors=200:stats_mode=diff[p];[b][p]paletteuse=dither=sierra2_4a:diff_mode=rectangle" \
    -loop 0 "$OUT/$name.gif"
}

wanted=${1:-}
for entry in "${FLOWS[@]}"; do
  read -r name rest <<<"$entry"
  [[ -n "$wanted" && "$wanted" != "$name" ]] && continue
  # shellcheck disable=SC2086
  build_flow "$name" $rest || true
done

echo "Done. GIFs in $OUT/"
ls -la "$OUT/"
