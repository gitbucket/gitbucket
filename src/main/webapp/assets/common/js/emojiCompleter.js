(function(window) {
  var emojiTable = [
    {
      "name": ":100:",
      "description": ":100: 💯"
    },
    {
      "name": ":1234:",
      "description": ":1234: 🔢"
    },
    {
      "name": ":+1:",
      "description": ":+1: 👍"
    },
    {
      "name": ":-1:",
      "description": ":-1: 👎"
    },
    {
      "name": ":1st_place_medal:",
      "description": ":1st_place_medal: 🥇"
    },
    {
      "name": ":2nd_place_medal:",
      "description": ":2nd_place_medal: 🥈"
    },
    {
      "name": ":3rd_place_medal:",
      "description": ":3rd_place_medal: 🥉"
    },
    {
      "name": ":8ball:",
      "description": ":8ball: 🎱"
    },
    {
      "name": ":a:",
      "description": ":a: 🅰"
    },
    {
      "name": ":ab:",
      "description": ":ab: 🆎"
    },
    {
      "name": ":abacus:",
      "description": ":abacus: 🧮"
    },
    {
      "name": ":abc:",
      "description": ":abc: 🔤"
    },
    {
      "name": ":abcd:",
      "description": ":abcd: 🔡"
    },
    {
      "name": ":accept:",
      "description": ":accept: 🉑"
    },
    {
      "name": ":accordion:",
      "description": ":accordion: 🪗"
    },
    {
      "name": ":adhesive_bandage:",
      "description": ":adhesive_bandage: 🩹"
    },
    {
      "name": ":adult:",
      "description": ":adult: 🧑"
    },
    {
      "name": ":aerial_tramway:",
      "description": ":aerial_tramway: 🚡"
    },
    {
      "name": ":afghanistan:",
      "description": ":afghanistan: 🇦🇫"
    },
    {
      "name": ":airplane:",
      "description": ":airplane: ✈"
    },
    {
      "name": ":aland_islands:",
      "description": ":aland_islands: 🇦🇽"
    },
    {
      "name": ":alarm_clock:",
      "description": ":alarm_clock: ⏰"
    },
    {
      "name": ":albania:",
      "description": ":albania: 🇦🇱"
    },
    {
      "name": ":alembic:",
      "description": ":alembic: ⚗"
    },
    {
      "name": ":algeria:",
      "description": ":algeria: 🇩🇿"
    },
    {
      "name": ":alien:",
      "description": ":alien: 👽"
    },
    {
      "name": ":ambulance:",
      "description": ":ambulance: 🚑"
    },
    {
      "name": ":american_samoa:",
      "description": ":american_samoa: 🇦🇸"
    },
    {
      "name": ":amphora:",
      "description": ":amphora: 🏺"
    },
    {
      "name": ":anatomical_heart:",
      "description": ":anatomical_heart: 🫀"
    },
    {
      "name": ":anchor:",
      "description": ":anchor: ⚓"
    },
    {
      "name": ":andorra:",
      "description": ":andorra: 🇦🇩"
    },
    {
      "name": ":angel:",
      "description": ":angel: 👼"
    },
    {
      "name": ":anger:",
      "description": ":anger: 💢"
    },
    {
      "name": ":angola:",
      "description": ":angola: 🇦🇴"
    },
    {
      "name": ":angry:",
      "description": ":angry: 😠"
    },
    {
      "name": ":anguilla:",
      "description": ":anguilla: 🇦🇮"
    },
    {
      "name": ":anguished:",
      "description": ":anguished: 😧"
    },
    {
      "name": ":ant:",
      "description": ":ant: 🐜"
    },
    {
      "name": ":antarctica:",
      "description": ":antarctica: 🇦🇶"
    },
    {
      "name": ":antigua_barbuda:",
      "description": ":antigua_barbuda: 🇦🇬"
    },
    {
      "name": ":apple:",
      "description": ":apple: 🍎"
    },
    {
      "name": ":aquarius:",
      "description": ":aquarius: ♒"
    },
    {
      "name": ":argentina:",
      "description": ":argentina: 🇦🇷"
    },
    {
      "name": ":aries:",
      "description": ":aries: ♈"
    },
    {
      "name": ":armenia:",
      "description": ":armenia: 🇦🇲"
    },
    {
      "name": ":arrow_backward:",
      "description": ":arrow_backward: ◀"
    },
    {
      "name": ":arrow_double_down:",
      "description": ":arrow_double_down: ⏬"
    },
    {
      "name": ":arrow_double_up:",
      "description": ":arrow_double_up: ⏫"
    },
    {
      "name": ":arrow_down:",
      "description": ":arrow_down: ⬇"
    },
    {
      "name": ":arrow_down_small:",
      "description": ":arrow_down_small: 🔽"
    },
    {
      "name": ":arrow_forward:",
      "description": ":arrow_forward: ▶"
    },
    {
      "name": ":arrow_heading_down:",
      "description": ":arrow_heading_down: ⤵"
    },
    {
      "name": ":arrow_heading_up:",
      "description": ":arrow_heading_up: ⤴"
    },
    {
      "name": ":arrow_left:",
      "description": ":arrow_left: ⬅"
    },
    {
      "name": ":arrow_lower_left:",
      "description": ":arrow_lower_left: ↙"
    },
    {
      "name": ":arrow_lower_right:",
      "description": ":arrow_lower_right: ↘"
    },
    {
      "name": ":arrow_right:",
      "description": ":arrow_right: ➡"
    },
    {
      "name": ":arrow_right_hook:",
      "description": ":arrow_right_hook: ↪"
    },
    {
      "name": ":arrow_up:",
      "description": ":arrow_up: ⬆"
    },
    {
      "name": ":arrow_up_down:",
      "description": ":arrow_up_down: ↕"
    },
    {
      "name": ":arrow_up_small:",
      "description": ":arrow_up_small: 🔼"
    },
    {
      "name": ":arrow_upper_left:",
      "description": ":arrow_upper_left: ↖"
    },
    {
      "name": ":arrow_upper_right:",
      "description": ":arrow_upper_right: ↗"
    },
    {
      "name": ":arrows_clockwise:",
      "description": ":arrows_clockwise: 🔃"
    },
    {
      "name": ":arrows_counterclockwise:",
      "description": ":arrows_counterclockwise: 🔄"
    },
    {
      "name": ":art:",
      "description": ":art: 🎨"
    },
    {
      "name": ":articulated_lorry:",
      "description": ":articulated_lorry: 🚛"
    },
    {
      "name": ":artificial_satellite:",
      "description": ":artificial_satellite: 🛰"
    },
    {
      "name": ":artist:",
      "description": ":artist: 🧑🎨"
    },
    {
      "name": ":aruba:",
      "description": ":aruba: 🇦🇼"
    },
    {
      "name": ":ascension_island:",
      "description": ":ascension_island: 🇦🇨"
    },
    {
      "name": ":asterisk:",
      "description": ":asterisk: *⃣"
    },
    {
      "name": ":astonished:",
      "description": ":astonished: 😲"
    },
    {
      "name": ":astronaut:",
      "description": ":astronaut: 🧑🚀"
    },
    {
      "name": ":athletic_shoe:",
      "description": ":athletic_shoe: 👟"
    },
    {
      "name": ":atm:",
      "description": ":atm: 🏧"
    },
    {
      "name": ":atom_symbol:",
      "description": ":atom_symbol: ⚛"
    },
    {
      "name": ":australia:",
      "description": ":australia: 🇦🇺"
    },
    {
      "name": ":austria:",
      "description": ":austria: 🇦🇹"
    },
    {
      "name": ":auto_rickshaw:",
      "description": ":auto_rickshaw: 🛺"
    },
    {
      "name": ":avocado:",
      "description": ":avocado: 🥑"
    },
    {
      "name": ":axe:",
      "description": ":axe: 🪓"
    },
    {
      "name": ":azerbaijan:",
      "description": ":azerbaijan: 🇦🇿"
    },
    {
      "name": ":b:",
      "description": ":b: 🅱"
    },
    {
      "name": ":baby:",
      "description": ":baby: 👶"
    },
    {
      "name": ":baby_bottle:",
      "description": ":baby_bottle: 🍼"
    },
    {
      "name": ":baby_chick:",
      "description": ":baby_chick: 🐤"
    },
    {
      "name": ":baby_symbol:",
      "description": ":baby_symbol: 🚼"
    },
    {
      "name": ":back:",
      "description": ":back: 🔙"
    },
    {
      "name": ":bacon:",
      "description": ":bacon: 🥓"
    },
    {
      "name": ":badger:",
      "description": ":badger: 🦡"
    },
    {
      "name": ":badminton:",
      "description": ":badminton: 🏸"
    },
    {
      "name": ":bagel:",
      "description": ":bagel: 🥯"
    },
    {
      "name": ":baggage_claim:",
      "description": ":baggage_claim: 🛄"
    },
    {
      "name": ":baguette_bread:",
      "description": ":baguette_bread: 🥖"
    },
    {
      "name": ":bahamas:",
      "description": ":bahamas: 🇧🇸"
    },
    {
      "name": ":bahrain:",
      "description": ":bahrain: 🇧🇭"
    },
    {
      "name": ":balance_scale:",
      "description": ":balance_scale: ⚖"
    },
    {
      "name": ":bald_man:",
      "description": ":bald_man: 👨🦲"
    },
    {
      "name": ":bald_woman:",
      "description": ":bald_woman: 👩🦲"
    },
    {
      "name": ":ballet_shoes:",
      "description": ":ballet_shoes: 🩰"
    },
    {
      "name": ":balloon:",
      "description": ":balloon: 🎈"
    },
    {
      "name": ":ballot_box:",
      "description": ":ballot_box: 🗳"
    },
    {
      "name": ":ballot_box_with_check:",
      "description": ":ballot_box_with_check: ☑"
    },
    {
      "name": ":bamboo:",
      "description": ":bamboo: 🎍"
    },
    {
      "name": ":banana:",
      "description": ":banana: 🍌"
    },
    {
      "name": ":bangbang:",
      "description": ":bangbang: ‼"
    },
    {
      "name": ":bangladesh:",
      "description": ":bangladesh: 🇧🇩"
    },
    {
      "name": ":banjo:",
      "description": ":banjo: 🪕"
    },
    {
      "name": ":bank:",
      "description": ":bank: 🏦"
    },
    {
      "name": ":bar_chart:",
      "description": ":bar_chart: 📊"
    },
    {
      "name": ":barbados:",
      "description": ":barbados: 🇧🇧"
    },
    {
      "name": ":barber:",
      "description": ":barber: 💈"
    },
    {
      "name": ":baseball:",
      "description": ":baseball: ⚾"
    },
    {
      "name": ":basket:",
      "description": ":basket: 🧺"
    },
    {
      "name": ":basketball:",
      "description": ":basketball: 🏀"
    },
    {
      "name": ":basketball_man:",
      "description": ":basketball_man: ⛹♂"
    },
    {
      "name": ":basketball_woman:",
      "description": ":basketball_woman: ⛹♀"
    },
    {
      "name": ":bat:",
      "description": ":bat: 🦇"
    },
    {
      "name": ":bath:",
      "description": ":bath: 🛀"
    },
    {
      "name": ":bathtub:",
      "description": ":bathtub: 🛁"
    },
    {
      "name": ":battery:",
      "description": ":battery: 🔋"
    },
    {
      "name": ":beach_umbrella:",
      "description": ":beach_umbrella: 🏖"
    },
    {
      "name": ":bear:",
      "description": ":bear: 🐻"
    },
    {
      "name": ":bearded_person:",
      "description": ":bearded_person: 🧔"
    },
    {
      "name": ":beaver:",
      "description": ":beaver: 🦫"
    },
    {
      "name": ":bed:",
      "description": ":bed: 🛏"
    },
    {
      "name": ":bee:",
      "description": ":bee: 🐝"
    },
    {
      "name": ":beer:",
      "description": ":beer: 🍺"
    },
    {
      "name": ":beers:",
      "description": ":beers: 🍻"
    },
    {
      "name": ":beetle:",
      "description": ":beetle: 🪲"
    },
    {
      "name": ":beginner:",
      "description": ":beginner: 🔰"
    },
    {
      "name": ":belarus:",
      "description": ":belarus: 🇧🇾"
    },
    {
      "name": ":belgium:",
      "description": ":belgium: 🇧🇪"
    },
    {
      "name": ":belize:",
      "description": ":belize: 🇧🇿"
    },
    {
      "name": ":bell:",
      "description": ":bell: 🔔"
    },
    {
      "name": ":bell_pepper:",
      "description": ":bell_pepper: 🫑"
    },
    {
      "name": ":bellhop_bell:",
      "description": ":bellhop_bell: 🛎"
    },
    {
      "name": ":benin:",
      "description": ":benin: 🇧🇯"
    },
    {
      "name": ":bento:",
      "description": ":bento: 🍱"
    },
    {
      "name": ":bermuda:",
      "description": ":bermuda: 🇧🇲"
    },
    {
      "name": ":beverage_box:",
      "description": ":beverage_box: 🧃"
    },
    {
      "name": ":bhutan:",
      "description": ":bhutan: 🇧🇹"
    },
    {
      "name": ":bicyclist:",
      "description": ":bicyclist: 🚴"
    },
    {
      "name": ":bike:",
      "description": ":bike: 🚲"
    },
    {
      "name": ":biking_man:",
      "description": ":biking_man: 🚴♂"
    },
    {
      "name": ":biking_woman:",
      "description": ":biking_woman: 🚴♀"
    },
    {
      "name": ":bikini:",
      "description": ":bikini: 👙"
    },
    {
      "name": ":billed_cap:",
      "description": ":billed_cap: 🧢"
    },
    {
      "name": ":biohazard:",
      "description": ":biohazard: ☣"
    },
    {
      "name": ":bird:",
      "description": ":bird: 🐦"
    },
    {
      "name": ":birthday:",
      "description": ":birthday: 🎂"
    },
    {
      "name": ":bison:",
      "description": ":bison: 🦬"
    },
    {
      "name": ":black_cat:",
      "description": ":black_cat: 🐈⬛"
    },
    {
      "name": ":black_circle:",
      "description": ":black_circle: ⚫"
    },
    {
      "name": ":black_flag:",
      "description": ":black_flag: 🏴"
    },
    {
      "name": ":black_heart:",
      "description": ":black_heart: 🖤"
    },
    {
      "name": ":black_joker:",
      "description": ":black_joker: 🃏"
    },
    {
      "name": ":black_large_square:",
      "description": ":black_large_square: ⬛"
    },
    {
      "name": ":black_medium_small_square:",
      "description": ":black_medium_small_square: ◾"
    },
    {
      "name": ":black_medium_square:",
      "description": ":black_medium_square: ◼"
    },
    {
      "name": ":black_nib:",
      "description": ":black_nib: ✒"
    },
    {
      "name": ":black_small_square:",
      "description": ":black_small_square: ▪"
    },
    {
      "name": ":black_square_button:",
      "description": ":black_square_button: 🔲"
    },
    {
      "name": ":blond_haired_man:",
      "description": ":blond_haired_man: 👱♂"
    },
    {
      "name": ":blond_haired_person:",
      "description": ":blond_haired_person: 👱"
    },
    {
      "name": ":blond_haired_woman:",
      "description": ":blond_haired_woman: 👱♀"
    },
    {
      "name": ":blonde_woman:",
      "description": ":blonde_woman: 👱♀"
    },
    {
      "name": ":blossom:",
      "description": ":blossom: 🌼"
    },
    {
      "name": ":blowfish:",
      "description": ":blowfish: 🐡"
    },
    {
      "name": ":blue_book:",
      "description": ":blue_book: 📘"
    },
    {
      "name": ":blue_car:",
      "description": ":blue_car: 🚙"
    },
    {
      "name": ":blue_heart:",
      "description": ":blue_heart: 💙"
    },
    {
      "name": ":blue_square:",
      "description": ":blue_square: 🟦"
    },
    {
      "name": ":blueberries:",
      "description": ":blueberries: 🫐"
    },
    {
      "name": ":blush:",
      "description": ":blush: 😊"
    },
    {
      "name": ":boar:",
      "description": ":boar: 🐗"
    },
    {
      "name": ":boat:",
      "description": ":boat: ⛵"
    },
    {
      "name": ":bolivia:",
      "description": ":bolivia: 🇧🇴"
    },
    {
      "name": ":bomb:",
      "description": ":bomb: 💣"
    },
    {
      "name": ":bone:",
      "description": ":bone: 🦴"
    },
    {
      "name": ":book:",
      "description": ":book: 📖"
    },
    {
      "name": ":bookmark:",
      "description": ":bookmark: 🔖"
    },
    {
      "name": ":bookmark_tabs:",
      "description": ":bookmark_tabs: 📑"
    },
    {
      "name": ":books:",
      "description": ":books: 📚"
    },
    {
      "name": ":boom:",
      "description": ":boom: 💥"
    },
    {
      "name": ":boomerang:",
      "description": ":boomerang: 🪃"
    },
    {
      "name": ":boot:",
      "description": ":boot: 👢"
    },
    {
      "name": ":bosnia_herzegovina:",
      "description": ":bosnia_herzegovina: 🇧🇦"
    },
    {
      "name": ":botswana:",
      "description": ":botswana: 🇧🇼"
    },
    {
      "name": ":bouncing_ball_man:",
      "description": ":bouncing_ball_man: ⛹♂"
    },
    {
      "name": ":bouncing_ball_person:",
      "description": ":bouncing_ball_person: ⛹"
    },
    {
      "name": ":bouncing_ball_woman:",
      "description": ":bouncing_ball_woman: ⛹♀"
    },
    {
      "name": ":bouquet:",
      "description": ":bouquet: 💐"
    },
    {
      "name": ":bouvet_island:",
      "description": ":bouvet_island: 🇧🇻"
    },
    {
      "name": ":bow:",
      "description": ":bow: 🙇"
    },
    {
      "name": ":bow_and_arrow:",
      "description": ":bow_and_arrow: 🏹"
    },
    {
      "name": ":bowing_man:",
      "description": ":bowing_man: 🙇♂"
    },
    {
      "name": ":bowing_woman:",
      "description": ":bowing_woman: 🙇♀"
    },
    {
      "name": ":bowl_with_spoon:",
      "description": ":bowl_with_spoon: 🥣"
    },
    {
      "name": ":bowling:",
      "description": ":bowling: 🎳"
    },
    {
      "name": ":boxing_glove:",
      "description": ":boxing_glove: 🥊"
    },
    {
      "name": ":boy:",
      "description": ":boy: 👦"
    },
    {
      "name": ":brain:",
      "description": ":brain: 🧠"
    },
    {
      "name": ":brazil:",
      "description": ":brazil: 🇧🇷"
    },
    {
      "name": ":bread:",
      "description": ":bread: 🍞"
    },
    {
      "name": ":breast_feeding:",
      "description": ":breast_feeding: 🤱"
    },
    {
      "name": ":bricks:",
      "description": ":bricks: 🧱"
    },
    {
      "name": ":bride_with_veil:",
      "description": ":bride_with_veil: 👰♀"
    },
    {
      "name": ":bridge_at_night:",
      "description": ":bridge_at_night: 🌉"
    },
    {
      "name": ":briefcase:",
      "description": ":briefcase: 💼"
    },
    {
      "name": ":british_indian_ocean_territory:",
      "description": ":british_indian_ocean_territory: 🇮🇴"
    },
    {
      "name": ":british_virgin_islands:",
      "description": ":british_virgin_islands: 🇻🇬"
    },
    {
      "name": ":broccoli:",
      "description": ":broccoli: 🥦"
    },
    {
      "name": ":broken_heart:",
      "description": ":broken_heart: 💔"
    },
    {
      "name": ":broom:",
      "description": ":broom: 🧹"
    },
    {
      "name": ":brown_circle:",
      "description": ":brown_circle: 🟤"
    },
    {
      "name": ":brown_heart:",
      "description": ":brown_heart: 🤎"
    },
    {
      "name": ":brown_square:",
      "description": ":brown_square: 🟫"
    },
    {
      "name": ":brunei:",
      "description": ":brunei: 🇧🇳"
    },
    {
      "name": ":bubble_tea:",
      "description": ":bubble_tea: 🧋"
    },
    {
      "name": ":bucket:",
      "description": ":bucket: 🪣"
    },
    {
      "name": ":bug:",
      "description": ":bug: 🐛"
    },
    {
      "name": ":building_construction:",
      "description": ":building_construction: 🏗"
    },
    {
      "name": ":bulb:",
      "description": ":bulb: 💡"
    },
    {
      "name": ":bulgaria:",
      "description": ":bulgaria: 🇧🇬"
    },
    {
      "name": ":bullettrain_front:",
      "description": ":bullettrain_front: 🚅"
    },
    {
      "name": ":bullettrain_side:",
      "description": ":bullettrain_side: 🚄"
    },
    {
      "name": ":burkina_faso:",
      "description": ":burkina_faso: 🇧🇫"
    },
    {
      "name": ":burrito:",
      "description": ":burrito: 🌯"
    },
    {
      "name": ":burundi:",
      "description": ":burundi: 🇧🇮"
    },
    {
      "name": ":bus:",
      "description": ":bus: 🚌"
    },
    {
      "name": ":business_suit_levitating:",
      "description": ":business_suit_levitating: 🕴"
    },
    {
      "name": ":busstop:",
      "description": ":busstop: 🚏"
    },
    {
      "name": ":bust_in_silhouette:",
      "description": ":bust_in_silhouette: 👤"
    },
    {
      "name": ":busts_in_silhouette:",
      "description": ":busts_in_silhouette: 👥"
    },
    {
      "name": ":butter:",
      "description": ":butter: 🧈"
    },
    {
      "name": ":butterfly:",
      "description": ":butterfly: 🦋"
    },
    {
      "name": ":cactus:",
      "description": ":cactus: 🌵"
    },
    {
      "name": ":cake:",
      "description": ":cake: 🍰"
    },
    {
      "name": ":calendar:",
      "description": ":calendar: 📆"
    },
    {
      "name": ":call_me_hand:",
      "description": ":call_me_hand: 🤙"
    },
    {
      "name": ":calling:",
      "description": ":calling: 📲"
    },
    {
      "name": ":cambodia:",
      "description": ":cambodia: 🇰🇭"
    },
    {
      "name": ":camel:",
      "description": ":camel: 🐫"
    },
    {
      "name": ":camera:",
      "description": ":camera: 📷"
    },
    {
      "name": ":camera_flash:",
      "description": ":camera_flash: 📸"
    },
    {
      "name": ":cameroon:",
      "description": ":cameroon: 🇨🇲"
    },
    {
      "name": ":camping:",
      "description": ":camping: 🏕"
    },
    {
      "name": ":canada:",
      "description": ":canada: 🇨🇦"
    },
    {
      "name": ":canary_islands:",
      "description": ":canary_islands: 🇮🇨"
    },
    {
      "name": ":cancer:",
      "description": ":cancer: ♋"
    },
    {
      "name": ":candle:",
      "description": ":candle: 🕯"
    },
    {
      "name": ":candy:",
      "description": ":candy: 🍬"
    },
    {
      "name": ":canned_food:",
      "description": ":canned_food: 🥫"
    },
    {
      "name": ":canoe:",
      "description": ":canoe: 🛶"
    },
    {
      "name": ":cape_verde:",
      "description": ":cape_verde: 🇨🇻"
    },
    {
      "name": ":capital_abcd:",
      "description": ":capital_abcd: 🔠"
    },
    {
      "name": ":capricorn:",
      "description": ":capricorn: ♑"
    },
    {
      "name": ":car:",
      "description": ":car: 🚗"
    },
    {
      "name": ":card_file_box:",
      "description": ":card_file_box: 🗃"
    },
    {
      "name": ":card_index:",
      "description": ":card_index: 📇"
    },
    {
      "name": ":card_index_dividers:",
      "description": ":card_index_dividers: 🗂"
    },
    {
      "name": ":caribbean_netherlands:",
      "description": ":caribbean_netherlands: 🇧🇶"
    },
    {
      "name": ":carousel_horse:",
      "description": ":carousel_horse: 🎠"
    },
    {
      "name": ":carpentry_saw:",
      "description": ":carpentry_saw: 🪚"
    },
    {
      "name": ":carrot:",
      "description": ":carrot: 🥕"
    },
    {
      "name": ":cartwheeling:",
      "description": ":cartwheeling: 🤸"
    },
    {
      "name": ":cat:",
      "description": ":cat: 🐱"
    },
    {
      "name": ":cat2:",
      "description": ":cat2: 🐈"
    },
    {
      "name": ":cayman_islands:",
      "description": ":cayman_islands: 🇰🇾"
    },
    {
      "name": ":cd:",
      "description": ":cd: 💿"
    },
    {
      "name": ":central_african_republic:",
      "description": ":central_african_republic: 🇨🇫"
    },
    {
      "name": ":ceuta_melilla:",
      "description": ":ceuta_melilla: 🇪🇦"
    },
    {
      "name": ":chad:",
      "description": ":chad: 🇹🇩"
    },
    {
      "name": ":chains:",
      "description": ":chains: ⛓"
    },
    {
      "name": ":chair:",
      "description": ":chair: 🪑"
    },
    {
      "name": ":champagne:",
      "description": ":champagne: 🍾"
    },
    {
      "name": ":chart:",
      "description": ":chart: 💹"
    },
    {
      "name": ":chart_with_downwards_trend:",
      "description": ":chart_with_downwards_trend: 📉"
    },
    {
      "name": ":chart_with_upwards_trend:",
      "description": ":chart_with_upwards_trend: 📈"
    },
    {
      "name": ":checkered_flag:",
      "description": ":checkered_flag: 🏁"
    },
    {
      "name": ":cheese:",
      "description": ":cheese: 🧀"
    },
    {
      "name": ":cherries:",
      "description": ":cherries: 🍒"
    },
    {
      "name": ":cherry_blossom:",
      "description": ":cherry_blossom: 🌸"
    },
    {
      "name": ":chess_pawn:",
      "description": ":chess_pawn: ♟"
    },
    {
      "name": ":chestnut:",
      "description": ":chestnut: 🌰"
    },
    {
      "name": ":chicken:",
      "description": ":chicken: 🐔"
    },
    {
      "name": ":child:",
      "description": ":child: 🧒"
    },
    {
      "name": ":children_crossing:",
      "description": ":children_crossing: 🚸"
    },
    {
      "name": ":chile:",
      "description": ":chile: 🇨🇱"
    },
    {
      "name": ":chipmunk:",
      "description": ":chipmunk: 🐿"
    },
    {
      "name": ":chocolate_bar:",
      "description": ":chocolate_bar: 🍫"
    },
    {
      "name": ":chopsticks:",
      "description": ":chopsticks: 🥢"
    },
    {
      "name": ":christmas_island:",
      "description": ":christmas_island: 🇨🇽"
    },
    {
      "name": ":christmas_tree:",
      "description": ":christmas_tree: 🎄"
    },
    {
      "name": ":church:",
      "description": ":church: ⛪"
    },
    {
      "name": ":cinema:",
      "description": ":cinema: 🎦"
    },
    {
      "name": ":circus_tent:",
      "description": ":circus_tent: 🎪"
    },
    {
      "name": ":city_sunrise:",
      "description": ":city_sunrise: 🌇"
    },
    {
      "name": ":city_sunset:",
      "description": ":city_sunset: 🌆"
    },
    {
      "name": ":cityscape:",
      "description": ":cityscape: 🏙"
    },
    {
      "name": ":cl:",
      "description": ":cl: 🆑"
    },
    {
      "name": ":clamp:",
      "description": ":clamp: 🗜"
    },
    {
      "name": ":clap:",
      "description": ":clap: 👏"
    },
    {
      "name": ":clapper:",
      "description": ":clapper: 🎬"
    },
    {
      "name": ":classical_building:",
      "description": ":classical_building: 🏛"
    },
    {
      "name": ":climbing:",
      "description": ":climbing: 🧗"
    },
    {
      "name": ":climbing_man:",
      "description": ":climbing_man: 🧗♂"
    },
    {
      "name": ":climbing_woman:",
      "description": ":climbing_woman: 🧗♀"
    },
    {
      "name": ":clinking_glasses:",
      "description": ":clinking_glasses: 🥂"
    },
    {
      "name": ":clipboard:",
      "description": ":clipboard: 📋"
    },
    {
      "name": ":clipperton_island:",
      "description": ":clipperton_island: 🇨🇵"
    },
    {
      "name": ":clock1:",
      "description": ":clock1: 🕐"
    },
    {
      "name": ":clock10:",
      "description": ":clock10: 🕙"
    },
    {
      "name": ":clock1030:",
      "description": ":clock1030: 🕥"
    },
    {
      "name": ":clock11:",
      "description": ":clock11: 🕚"
    },
    {
      "name": ":clock1130:",
      "description": ":clock1130: 🕦"
    },
    {
      "name": ":clock12:",
      "description": ":clock12: 🕛"
    },
    {
      "name": ":clock1230:",
      "description": ":clock1230: 🕧"
    },
    {
      "name": ":clock130:",
      "description": ":clock130: 🕜"
    },
    {
      "name": ":clock2:",
      "description": ":clock2: 🕑"
    },
    {
      "name": ":clock230:",
      "description": ":clock230: 🕝"
    },
    {
      "name": ":clock3:",
      "description": ":clock3: 🕒"
    },
    {
      "name": ":clock330:",
      "description": ":clock330: 🕞"
    },
    {
      "name": ":clock4:",
      "description": ":clock4: 🕓"
    },
    {
      "name": ":clock430:",
      "description": ":clock430: 🕟"
    },
    {
      "name": ":clock5:",
      "description": ":clock5: 🕔"
    },
    {
      "name": ":clock530:",
      "description": ":clock530: 🕠"
    },
    {
      "name": ":clock6:",
      "description": ":clock6: 🕕"
    },
    {
      "name": ":clock630:",
      "description": ":clock630: 🕡"
    },
    {
      "name": ":clock7:",
      "description": ":clock7: 🕖"
    },
    {
      "name": ":clock730:",
      "description": ":clock730: 🕢"
    },
    {
      "name": ":clock8:",
      "description": ":clock8: 🕗"
    },
    {
      "name": ":clock830:",
      "description": ":clock830: 🕣"
    },
    {
      "name": ":clock9:",
      "description": ":clock9: 🕘"
    },
    {
      "name": ":clock930:",
      "description": ":clock930: 🕤"
    },
    {
      "name": ":closed_book:",
      "description": ":closed_book: 📕"
    },
    {
      "name": ":closed_lock_with_key:",
      "description": ":closed_lock_with_key: 🔐"
    },
    {
      "name": ":closed_umbrella:",
      "description": ":closed_umbrella: 🌂"
    },
    {
      "name": ":cloud:",
      "description": ":cloud: ☁"
    },
    {
      "name": ":cloud_with_lightning:",
      "description": ":cloud_with_lightning: 🌩"
    },
    {
      "name": ":cloud_with_lightning_and_rain:",
      "description": ":cloud_with_lightning_and_rain: ⛈"
    },
    {
      "name": ":cloud_with_rain:",
      "description": ":cloud_with_rain: 🌧"
    },
    {
      "name": ":cloud_with_snow:",
      "description": ":cloud_with_snow: 🌨"
    },
    {
      "name": ":clown_face:",
      "description": ":clown_face: 🤡"
    },
    {
      "name": ":clubs:",
      "description": ":clubs: ♣"
    },
    {
      "name": ":cn:",
      "description": ":cn: 🇨🇳"
    },
    {
      "name": ":coat:",
      "description": ":coat: 🧥"
    },
    {
      "name": ":cockroach:",
      "description": ":cockroach: 🪳"
    },
    {
      "name": ":cocktail:",
      "description": ":cocktail: 🍸"
    },
    {
      "name": ":coconut:",
      "description": ":coconut: 🥥"
    },
    {
      "name": ":cocos_islands:",
      "description": ":cocos_islands: 🇨🇨"
    },
    {
      "name": ":coffee:",
      "description": ":coffee: ☕"
    },
    {
      "name": ":coffin:",
      "description": ":coffin: ⚰"
    },
    {
      "name": ":coin:",
      "description": ":coin: 🪙"
    },
    {
      "name": ":cold_face:",
      "description": ":cold_face: 🥶"
    },
    {
      "name": ":cold_sweat:",
      "description": ":cold_sweat: 😰"
    },
    {
      "name": ":collision:",
      "description": ":collision: 💥"
    },
    {
      "name": ":colombia:",
      "description": ":colombia: 🇨🇴"
    },
    {
      "name": ":comet:",
      "description": ":comet: ☄"
    },
    {
      "name": ":comoros:",
      "description": ":comoros: 🇰🇲"
    },
    {
      "name": ":compass:",
      "description": ":compass: 🧭"
    },
    {
      "name": ":computer:",
      "description": ":computer: 💻"
    },
    {
      "name": ":computer_mouse:",
      "description": ":computer_mouse: 🖱"
    },
    {
      "name": ":confetti_ball:",
      "description": ":confetti_ball: 🎊"
    },
    {
      "name": ":confounded:",
      "description": ":confounded: 😖"
    },
    {
      "name": ":confused:",
      "description": ":confused: 😕"
    },
    {
      "name": ":congo_brazzaville:",
      "description": ":congo_brazzaville: 🇨🇬"
    },
    {
      "name": ":congo_kinshasa:",
      "description": ":congo_kinshasa: 🇨🇩"
    },
    {
      "name": ":congratulations:",
      "description": ":congratulations: ㊗"
    },
    {
      "name": ":construction:",
      "description": ":construction: 🚧"
    },
    {
      "name": ":construction_worker:",
      "description": ":construction_worker: 👷"
    },
    {
      "name": ":construction_worker_man:",
      "description": ":construction_worker_man: 👷♂"
    },
    {
      "name": ":construction_worker_woman:",
      "description": ":construction_worker_woman: 👷♀"
    },
    {
      "name": ":control_knobs:",
      "description": ":control_knobs: 🎛"
    },
    {
      "name": ":convenience_store:",
      "description": ":convenience_store: 🏪"
    },
    {
      "name": ":cook:",
      "description": ":cook: 🧑🍳"
    },
    {
      "name": ":cook_islands:",
      "description": ":cook_islands: 🇨🇰"
    },
    {
      "name": ":cookie:",
      "description": ":cookie: 🍪"
    },
    {
      "name": ":cool:",
      "description": ":cool: 🆒"
    },
    {
      "name": ":cop:",
      "description": ":cop: 👮"
    },
    {
      "name": ":copyright:",
      "description": ":copyright: ©"
    },
    {
      "name": ":corn:",
      "description": ":corn: 🌽"
    },
    {
      "name": ":costa_rica:",
      "description": ":costa_rica: 🇨🇷"
    },
    {
      "name": ":cote_divoire:",
      "description": ":cote_divoire: 🇨🇮"
    },
    {
      "name": ":couch_and_lamp:",
      "description": ":couch_and_lamp: 🛋"
    },
    {
      "name": ":couple:",
      "description": ":couple: 👫"
    },
    {
      "name": ":couple_with_heart:",
      "description": ":couple_with_heart: 💑"
    },
    {
      "name": ":couple_with_heart_man_man:",
      "description": ":couple_with_heart_man_man: 👨❤👨"
    },
    {
      "name": ":couple_with_heart_woman_man:",
      "description": ":couple_with_heart_woman_man: 👩❤👨"
    },
    {
      "name": ":couple_with_heart_woman_woman:",
      "description": ":couple_with_heart_woman_woman: 👩❤👩"
    },
    {
      "name": ":couplekiss:",
      "description": ":couplekiss: 💏"
    },
    {
      "name": ":couplekiss_man_man:",
      "description": ":couplekiss_man_man: 👨❤💋👨"
    },
    {
      "name": ":couplekiss_man_woman:",
      "description": ":couplekiss_man_woman: 👩❤💋👨"
    },
    {
      "name": ":couplekiss_woman_woman:",
      "description": ":couplekiss_woman_woman: 👩❤💋👩"
    },
    {
      "name": ":cow:",
      "description": ":cow: 🐮"
    },
    {
      "name": ":cow2:",
      "description": ":cow2: 🐄"
    },
    {
      "name": ":cowboy_hat_face:",
      "description": ":cowboy_hat_face: 🤠"
    },
    {
      "name": ":crab:",
      "description": ":crab: 🦀"
    },
    {
      "name": ":crayon:",
      "description": ":crayon: 🖍"
    },
    {
      "name": ":credit_card:",
      "description": ":credit_card: 💳"
    },
    {
      "name": ":crescent_moon:",
      "description": ":crescent_moon: 🌙"
    },
    {
      "name": ":cricket:",
      "description": ":cricket: 🦗"
    },
    {
      "name": ":cricket_game:",
      "description": ":cricket_game: 🏏"
    },
    {
      "name": ":croatia:",
      "description": ":croatia: 🇭🇷"
    },
    {
      "name": ":crocodile:",
      "description": ":crocodile: 🐊"
    },
    {
      "name": ":croissant:",
      "description": ":croissant: 🥐"
    },
    {
      "name": ":crossed_fingers:",
      "description": ":crossed_fingers: 🤞"
    },
    {
      "name": ":crossed_flags:",
      "description": ":crossed_flags: 🎌"
    },
    {
      "name": ":crossed_swords:",
      "description": ":crossed_swords: ⚔"
    },
    {
      "name": ":crown:",
      "description": ":crown: 👑"
    },
    {
      "name": ":cry:",
      "description": ":cry: 😢"
    },
    {
      "name": ":crying_cat_face:",
      "description": ":crying_cat_face: 😿"
    },
    {
      "name": ":crystal_ball:",
      "description": ":crystal_ball: 🔮"
    },
    {
      "name": ":cuba:",
      "description": ":cuba: 🇨🇺"
    },
    {
      "name": ":cucumber:",
      "description": ":cucumber: 🥒"
    },
    {
      "name": ":cup_with_straw:",
      "description": ":cup_with_straw: 🥤"
    },
    {
      "name": ":cupcake:",
      "description": ":cupcake: 🧁"
    },
    {
      "name": ":cupid:",
      "description": ":cupid: 💘"
    },
    {
      "name": ":curacao:",
      "description": ":curacao: 🇨🇼"
    },
    {
      "name": ":curling_stone:",
      "description": ":curling_stone: 🥌"
    },
    {
      "name": ":curly_haired_man:",
      "description": ":curly_haired_man: 👨🦱"
    },
    {
      "name": ":curly_haired_woman:",
      "description": ":curly_haired_woman: 👩🦱"
    },
    {
      "name": ":curly_loop:",
      "description": ":curly_loop: ➰"
    },
    {
      "name": ":currency_exchange:",
      "description": ":currency_exchange: 💱"
    },
    {
      "name": ":curry:",
      "description": ":curry: 🍛"
    },
    {
      "name": ":cursing_face:",
      "description": ":cursing_face: 🤬"
    },
    {
      "name": ":custard:",
      "description": ":custard: 🍮"
    },
    {
      "name": ":customs:",
      "description": ":customs: 🛃"
    },
    {
      "name": ":cut_of_meat:",
      "description": ":cut_of_meat: 🥩"
    },
    {
      "name": ":cyclone:",
      "description": ":cyclone: 🌀"
    },
    {
      "name": ":cyprus:",
      "description": ":cyprus: 🇨🇾"
    },
    {
      "name": ":czech_republic:",
      "description": ":czech_republic: 🇨🇿"
    },
    {
      "name": ":dagger:",
      "description": ":dagger: 🗡"
    },
    {
      "name": ":dancer:",
      "description": ":dancer: 💃"
    },
    {
      "name": ":dancers:",
      "description": ":dancers: 👯"
    },
    {
      "name": ":dancing_men:",
      "description": ":dancing_men: 👯♂"
    },
    {
      "name": ":dancing_women:",
      "description": ":dancing_women: 👯♀"
    },
    {
      "name": ":dango:",
      "description": ":dango: 🍡"
    },
    {
      "name": ":dark_sunglasses:",
      "description": ":dark_sunglasses: 🕶"
    },
    {
      "name": ":dart:",
      "description": ":dart: 🎯"
    },
    {
      "name": ":dash:",
      "description": ":dash: 💨"
    },
    {
      "name": ":date:",
      "description": ":date: 📅"
    },
    {
      "name": ":de:",
      "description": ":de: 🇩🇪"
    },
    {
      "name": ":deaf_man:",
      "description": ":deaf_man: 🧏♂"
    },
    {
      "name": ":deaf_person:",
      "description": ":deaf_person: 🧏"
    },
    {
      "name": ":deaf_woman:",
      "description": ":deaf_woman: 🧏♀"
    },
    {
      "name": ":deciduous_tree:",
      "description": ":deciduous_tree: 🌳"
    },
    {
      "name": ":deer:",
      "description": ":deer: 🦌"
    },
    {
      "name": ":denmark:",
      "description": ":denmark: 🇩🇰"
    },
    {
      "name": ":department_store:",
      "description": ":department_store: 🏬"
    },
    {
      "name": ":derelict_house:",
      "description": ":derelict_house: 🏚"
    },
    {
      "name": ":desert:",
      "description": ":desert: 🏜"
    },
    {
      "name": ":desert_island:",
      "description": ":desert_island: 🏝"
    },
    {
      "name": ":desktop_computer:",
      "description": ":desktop_computer: 🖥"
    },
    {
      "name": ":detective:",
      "description": ":detective: 🕵"
    },
    {
      "name": ":diamond_shape_with_a_dot_inside:",
      "description": ":diamond_shape_with_a_dot_inside: 💠"
    },
    {
      "name": ":diamonds:",
      "description": ":diamonds: ♦"
    },
    {
      "name": ":diego_garcia:",
      "description": ":diego_garcia: 🇩🇬"
    },
    {
      "name": ":disappointed:",
      "description": ":disappointed: 😞"
    },
    {
      "name": ":disappointed_relieved:",
      "description": ":disappointed_relieved: 😥"
    },
    {
      "name": ":disguised_face:",
      "description": ":disguised_face: 🥸"
    },
    {
      "name": ":diving_mask:",
      "description": ":diving_mask: 🤿"
    },
    {
      "name": ":diya_lamp:",
      "description": ":diya_lamp: 🪔"
    },
    {
      "name": ":dizzy:",
      "description": ":dizzy: 💫"
    },
    {
      "name": ":dizzy_face:",
      "description": ":dizzy_face: 😵"
    },
    {
      "name": ":djibouti:",
      "description": ":djibouti: 🇩🇯"
    },
    {
      "name": ":dna:",
      "description": ":dna: 🧬"
    },
    {
      "name": ":do_not_litter:",
      "description": ":do_not_litter: 🚯"
    },
    {
      "name": ":dodo:",
      "description": ":dodo: 🦤"
    },
    {
      "name": ":dog:",
      "description": ":dog: 🐶"
    },
    {
      "name": ":dog2:",
      "description": ":dog2: 🐕"
    },
    {
      "name": ":dollar:",
      "description": ":dollar: 💵"
    },
    {
      "name": ":dolls:",
      "description": ":dolls: 🎎"
    },
    {
      "name": ":dolphin:",
      "description": ":dolphin: 🐬"
    },
    {
      "name": ":dominica:",
      "description": ":dominica: 🇩🇲"
    },
    {
      "name": ":dominican_republic:",
      "description": ":dominican_republic: 🇩🇴"
    },
    {
      "name": ":door:",
      "description": ":door: 🚪"
    },
    {
      "name": ":doughnut:",
      "description": ":doughnut: 🍩"
    },
    {
      "name": ":dove:",
      "description": ":dove: 🕊"
    },
    {
      "name": ":dragon:",
      "description": ":dragon: 🐉"
    },
    {
      "name": ":dragon_face:",
      "description": ":dragon_face: 🐲"
    },
    {
      "name": ":dress:",
      "description": ":dress: 👗"
    },
    {
      "name": ":dromedary_camel:",
      "description": ":dromedary_camel: 🐪"
    },
    {
      "name": ":drooling_face:",
      "description": ":drooling_face: 🤤"
    },
    {
      "name": ":drop_of_blood:",
      "description": ":drop_of_blood: 🩸"
    },
    {
      "name": ":droplet:",
      "description": ":droplet: 💧"
    },
    {
      "name": ":drum:",
      "description": ":drum: 🥁"
    },
    {
      "name": ":duck:",
      "description": ":duck: 🦆"
    },
    {
      "name": ":dumpling:",
      "description": ":dumpling: 🥟"
    },
    {
      "name": ":dvd:",
      "description": ":dvd: 📀"
    },
    {
      "name": ":e-mail:",
      "description": ":e-mail: 📧"
    },
    {
      "name": ":eagle:",
      "description": ":eagle: 🦅"
    },
    {
      "name": ":ear:",
      "description": ":ear: 👂"
    },
    {
      "name": ":ear_of_rice:",
      "description": ":ear_of_rice: 🌾"
    },
    {
      "name": ":ear_with_hearing_aid:",
      "description": ":ear_with_hearing_aid: 🦻"
    },
    {
      "name": ":earth_africa:",
      "description": ":earth_africa: 🌍"
    },
    {
      "name": ":earth_americas:",
      "description": ":earth_americas: 🌎"
    },
    {
      "name": ":earth_asia:",
      "description": ":earth_asia: 🌏"
    },
    {
      "name": ":ecuador:",
      "description": ":ecuador: 🇪🇨"
    },
    {
      "name": ":egg:",
      "description": ":egg: 🥚"
    },
    {
      "name": ":eggplant:",
      "description": ":eggplant: 🍆"
    },
    {
      "name": ":egypt:",
      "description": ":egypt: 🇪🇬"
    },
    {
      "name": ":eight:",
      "description": ":eight: 8⃣"
    },
    {
      "name": ":eight_pointed_black_star:",
      "description": ":eight_pointed_black_star: ✴"
    },
    {
      "name": ":eight_spoked_asterisk:",
      "description": ":eight_spoked_asterisk: ✳"
    },
    {
      "name": ":eject_button:",
      "description": ":eject_button: ⏏"
    },
    {
      "name": ":el_salvador:",
      "description": ":el_salvador: 🇸🇻"
    },
    {
      "name": ":electric_plug:",
      "description": ":electric_plug: 🔌"
    },
    {
      "name": ":elephant:",
      "description": ":elephant: 🐘"
    },
    {
      "name": ":elevator:",
      "description": ":elevator: 🛗"
    },
    {
      "name": ":elf:",
      "description": ":elf: 🧝"
    },
    {
      "name": ":elf_man:",
      "description": ":elf_man: 🧝♂"
    },
    {
      "name": ":elf_woman:",
      "description": ":elf_woman: 🧝♀"
    },
    {
      "name": ":email:",
      "description": ":email: 📧"
    },
    {
      "name": ":end:",
      "description": ":end: 🔚"
    },
    {
      "name": ":england:",
      "description": ":england: 🏴󠁧󠁢󠁥󠁮󠁧󠁿"
    },
    {
      "name": ":envelope:",
      "description": ":envelope: ✉"
    },
    {
      "name": ":envelope_with_arrow:",
      "description": ":envelope_with_arrow: 📩"
    },
    {
      "name": ":equatorial_guinea:",
      "description": ":equatorial_guinea: 🇬🇶"
    },
    {
      "name": ":eritrea:",
      "description": ":eritrea: 🇪🇷"
    },
    {
      "name": ":es:",
      "description": ":es: 🇪🇸"
    },
    {
      "name": ":estonia:",
      "description": ":estonia: 🇪🇪"
    },
    {
      "name": ":ethiopia:",
      "description": ":ethiopia: 🇪🇹"
    },
    {
      "name": ":eu:",
      "description": ":eu: 🇪🇺"
    },
    {
      "name": ":euro:",
      "description": ":euro: 💶"
    },
    {
      "name": ":european_castle:",
      "description": ":european_castle: 🏰"
    },
    {
      "name": ":european_post_office:",
      "description": ":european_post_office: 🏤"
    },
    {
      "name": ":european_union:",
      "description": ":european_union: 🇪🇺"
    },
    {
      "name": ":evergreen_tree:",
      "description": ":evergreen_tree: 🌲"
    },
    {
      "name": ":exclamation:",
      "description": ":exclamation: ❗"
    },
    {
      "name": ":exploding_head:",
      "description": ":exploding_head: 🤯"
    },
    {
      "name": ":expressionless:",
      "description": ":expressionless: 😑"
    },
    {
      "name": ":eye:",
      "description": ":eye: 👁"
    },
    {
      "name": ":eye_speech_bubble:",
      "description": ":eye_speech_bubble: 👁🗨"
    },
    {
      "name": ":eyeglasses:",
      "description": ":eyeglasses: 👓"
    },
    {
      "name": ":eyes:",
      "description": ":eyes: 👀"
    },
    {
      "name": ":face_exhaling:",
      "description": ":face_exhaling: 😮💨"
    },
    {
      "name": ":face_in_clouds:",
      "description": ":face_in_clouds: 😶🌫"
    },
    {
      "name": ":face_with_head_bandage:",
      "description": ":face_with_head_bandage: 🤕"
    },
    {
      "name": ":face_with_spiral_eyes:",
      "description": ":face_with_spiral_eyes: 😵💫"
    },
    {
      "name": ":face_with_thermometer:",
      "description": ":face_with_thermometer: 🤒"
    },
    {
      "name": ":facepalm:",
      "description": ":facepalm: 🤦"
    },
    {
      "name": ":facepunch:",
      "description": ":facepunch: 👊"
    },
    {
      "name": ":factory:",
      "description": ":factory: 🏭"
    },
    {
      "name": ":factory_worker:",
      "description": ":factory_worker: 🧑🏭"
    },
    {
      "name": ":fairy:",
      "description": ":fairy: 🧚"
    },
    {
      "name": ":fairy_man:",
      "description": ":fairy_man: 🧚♂"
    },
    {
      "name": ":fairy_woman:",
      "description": ":fairy_woman: 🧚♀"
    },
    {
      "name": ":falafel:",
      "description": ":falafel: 🧆"
    },
    {
      "name": ":falkland_islands:",
      "description": ":falkland_islands: 🇫🇰"
    },
    {
      "name": ":fallen_leaf:",
      "description": ":fallen_leaf: 🍂"
    },
    {
      "name": ":family:",
      "description": ":family: 👪"
    },
    {
      "name": ":family_man_boy:",
      "description": ":family_man_boy: 👨👦"
    },
    {
      "name": ":family_man_boy_boy:",
      "description": ":family_man_boy_boy: 👨👦👦"
    },
    {
      "name": ":family_man_girl:",
      "description": ":family_man_girl: 👨👧"
    },
    {
      "name": ":family_man_girl_boy:",
      "description": ":family_man_girl_boy: 👨👧👦"
    },
    {
      "name": ":family_man_girl_girl:",
      "description": ":family_man_girl_girl: 👨👧👧"
    },
    {
      "name": ":family_man_man_boy:",
      "description": ":family_man_man_boy: 👨👨👦"
    },
    {
      "name": ":family_man_man_boy_boy:",
      "description": ":family_man_man_boy_boy: 👨👨👦👦"
    },
    {
      "name": ":family_man_man_girl:",
      "description": ":family_man_man_girl: 👨👨👧"
    },
    {
      "name": ":family_man_man_girl_boy:",
      "description": ":family_man_man_girl_boy: 👨👨👧👦"
    },
    {
      "name": ":family_man_man_girl_girl:",
      "description": ":family_man_man_girl_girl: 👨👨👧👧"
    },
    {
      "name": ":family_man_woman_boy:",
      "description": ":family_man_woman_boy: 👨👩👦"
    },
    {
      "name": ":family_man_woman_boy_boy:",
      "description": ":family_man_woman_boy_boy: 👨👩👦👦"
    },
    {
      "name": ":family_man_woman_girl:",
      "description": ":family_man_woman_girl: 👨👩👧"
    },
    {
      "name": ":family_man_woman_girl_boy:",
      "description": ":family_man_woman_girl_boy: 👨👩👧👦"
    },
    {
      "name": ":family_man_woman_girl_girl:",
      "description": ":family_man_woman_girl_girl: 👨👩👧👧"
    },
    {
      "name": ":family_woman_boy:",
      "description": ":family_woman_boy: 👩👦"
    },
    {
      "name": ":family_woman_boy_boy:",
      "description": ":family_woman_boy_boy: 👩👦👦"
    },
    {
      "name": ":family_woman_girl:",
      "description": ":family_woman_girl: 👩👧"
    },
    {
      "name": ":family_woman_girl_boy:",
      "description": ":family_woman_girl_boy: 👩👧👦"
    },
    {
      "name": ":family_woman_girl_girl:",
      "description": ":family_woman_girl_girl: 👩👧👧"
    },
    {
      "name": ":family_woman_woman_boy:",
      "description": ":family_woman_woman_boy: 👩👩👦"
    },
    {
      "name": ":family_woman_woman_boy_boy:",
      "description": ":family_woman_woman_boy_boy: 👩👩👦👦"
    },
    {
      "name": ":family_woman_woman_girl:",
      "description": ":family_woman_woman_girl: 👩👩👧"
    },
    {
      "name": ":family_woman_woman_girl_boy:",
      "description": ":family_woman_woman_girl_boy: 👩👩👧👦"
    },
    {
      "name": ":family_woman_woman_girl_girl:",
      "description": ":family_woman_woman_girl_girl: 👩👩👧👧"
    },
    {
      "name": ":farmer:",
      "description": ":farmer: 🧑🌾"
    },
    {
      "name": ":faroe_islands:",
      "description": ":faroe_islands: 🇫🇴"
    },
    {
      "name": ":fast_forward:",
      "description": ":fast_forward: ⏩"
    },
    {
      "name": ":fax:",
      "description": ":fax: 📠"
    },
    {
      "name": ":fearful:",
      "description": ":fearful: 😨"
    },
    {
      "name": ":feather:",
      "description": ":feather: 🪶"
    },
    {
      "name": ":feet:",
      "description": ":feet: 🐾"
    },
    {
      "name": ":female_detective:",
      "description": ":female_detective: 🕵♀"
    },
    {
      "name": ":female_sign:",
      "description": ":female_sign: ♀"
    },
    {
      "name": ":ferris_wheel:",
      "description": ":ferris_wheel: 🎡"
    },
    {
      "name": ":ferry:",
      "description": ":ferry: ⛴"
    },
    {
      "name": ":field_hockey:",
      "description": ":field_hockey: 🏑"
    },
    {
      "name": ":fiji:",
      "description": ":fiji: 🇫🇯"
    },
    {
      "name": ":file_cabinet:",
      "description": ":file_cabinet: 🗄"
    },
    {
      "name": ":file_folder:",
      "description": ":file_folder: 📁"
    },
    {
      "name": ":film_projector:",
      "description": ":film_projector: 📽"
    },
    {
      "name": ":film_strip:",
      "description": ":film_strip: 🎞"
    },
    {
      "name": ":finland:",
      "description": ":finland: 🇫🇮"
    },
    {
      "name": ":fire:",
      "description": ":fire: 🔥"
    },
    {
      "name": ":fire_engine:",
      "description": ":fire_engine: 🚒"
    },
    {
      "name": ":fire_extinguisher:",
      "description": ":fire_extinguisher: 🧯"
    },
    {
      "name": ":firecracker:",
      "description": ":firecracker: 🧨"
    },
    {
      "name": ":firefighter:",
      "description": ":firefighter: 🧑🚒"
    },
    {
      "name": ":fireworks:",
      "description": ":fireworks: 🎆"
    },
    {
      "name": ":first_quarter_moon:",
      "description": ":first_quarter_moon: 🌓"
    },
    {
      "name": ":first_quarter_moon_with_face:",
      "description": ":first_quarter_moon_with_face: 🌛"
    },
    {
      "name": ":fish:",
      "description": ":fish: 🐟"
    },
    {
      "name": ":fish_cake:",
      "description": ":fish_cake: 🍥"
    },
    {
      "name": ":fishing_pole_and_fish:",
      "description": ":fishing_pole_and_fish: 🎣"
    },
    {
      "name": ":fist:",
      "description": ":fist: ✊"
    },
    {
      "name": ":fist_left:",
      "description": ":fist_left: 🤛"
    },
    {
      "name": ":fist_oncoming:",
      "description": ":fist_oncoming: 👊"
    },
    {
      "name": ":fist_raised:",
      "description": ":fist_raised: ✊"
    },
    {
      "name": ":fist_right:",
      "description": ":fist_right: 🤜"
    },
    {
      "name": ":five:",
      "description": ":five: 5⃣"
    },
    {
      "name": ":flags:",
      "description": ":flags: 🎏"
    },
    {
      "name": ":flamingo:",
      "description": ":flamingo: 🦩"
    },
    {
      "name": ":flashlight:",
      "description": ":flashlight: 🔦"
    },
    {
      "name": ":flat_shoe:",
      "description": ":flat_shoe: 🥿"
    },
    {
      "name": ":flatbread:",
      "description": ":flatbread: 🫓"
    },
    {
      "name": ":fleur_de_lis:",
      "description": ":fleur_de_lis: ⚜"
    },
    {
      "name": ":flight_arrival:",
      "description": ":flight_arrival: 🛬"
    },
    {
      "name": ":flight_departure:",
      "description": ":flight_departure: 🛫"
    },
    {
      "name": ":flipper:",
      "description": ":flipper: 🐬"
    },
    {
      "name": ":floppy_disk:",
      "description": ":floppy_disk: 💾"
    },
    {
      "name": ":flower_playing_cards:",
      "description": ":flower_playing_cards: 🎴"
    },
    {
      "name": ":flushed:",
      "description": ":flushed: 😳"
    },
    {
      "name": ":fly:",
      "description": ":fly: 🪰"
    },
    {
      "name": ":flying_disc:",
      "description": ":flying_disc: 🥏"
    },
    {
      "name": ":flying_saucer:",
      "description": ":flying_saucer: 🛸"
    },
    {
      "name": ":fog:",
      "description": ":fog: 🌫"
    },
    {
      "name": ":foggy:",
      "description": ":foggy: 🌁"
    },
    {
      "name": ":fondue:",
      "description": ":fondue: 🫕"
    },
    {
      "name": ":foot:",
      "description": ":foot: 🦶"
    },
    {
      "name": ":football:",
      "description": ":football: 🏈"
    },
    {
      "name": ":footprints:",
      "description": ":footprints: 👣"
    },
    {
      "name": ":fork_and_knife:",
      "description": ":fork_and_knife: 🍴"
    },
    {
      "name": ":fortune_cookie:",
      "description": ":fortune_cookie: 🥠"
    },
    {
      "name": ":fountain:",
      "description": ":fountain: ⛲"
    },
    {
      "name": ":fountain_pen:",
      "description": ":fountain_pen: 🖋"
    },
    {
      "name": ":four:",
      "description": ":four: 4⃣"
    },
    {
      "name": ":four_leaf_clover:",
      "description": ":four_leaf_clover: 🍀"
    },
    {
      "name": ":fox_face:",
      "description": ":fox_face: 🦊"
    },
    {
      "name": ":fr:",
      "description": ":fr: 🇫🇷"
    },
    {
      "name": ":framed_picture:",
      "description": ":framed_picture: 🖼"
    },
    {
      "name": ":free:",
      "description": ":free: 🆓"
    },
    {
      "name": ":french_guiana:",
      "description": ":french_guiana: 🇬🇫"
    },
    {
      "name": ":french_polynesia:",
      "description": ":french_polynesia: 🇵🇫"
    },
    {
      "name": ":french_southern_territories:",
      "description": ":french_southern_territories: 🇹🇫"
    },
    {
      "name": ":fried_egg:",
      "description": ":fried_egg: 🍳"
    },
    {
      "name": ":fried_shrimp:",
      "description": ":fried_shrimp: 🍤"
    },
    {
      "name": ":fries:",
      "description": ":fries: 🍟"
    },
    {
      "name": ":frog:",
      "description": ":frog: 🐸"
    },
    {
      "name": ":frowning:",
      "description": ":frowning: 😦"
    },
    {
      "name": ":frowning_face:",
      "description": ":frowning_face: ☹"
    },
    {
      "name": ":frowning_man:",
      "description": ":frowning_man: 🙍♂"
    },
    {
      "name": ":frowning_person:",
      "description": ":frowning_person: 🙍"
    },
    {
      "name": ":frowning_woman:",
      "description": ":frowning_woman: 🙍♀"
    },
    {
      "name": ":fu:",
      "description": ":fu: 🖕"
    },
    {
      "name": ":fuelpump:",
      "description": ":fuelpump: ⛽"
    },
    {
      "name": ":full_moon:",
      "description": ":full_moon: 🌕"
    },
    {
      "name": ":full_moon_with_face:",
      "description": ":full_moon_with_face: 🌝"
    },
    {
      "name": ":funeral_urn:",
      "description": ":funeral_urn: ⚱"
    },
    {
      "name": ":gabon:",
      "description": ":gabon: 🇬🇦"
    },
    {
      "name": ":gambia:",
      "description": ":gambia: 🇬🇲"
    },
    {
      "name": ":game_die:",
      "description": ":game_die: 🎲"
    },
    {
      "name": ":garlic:",
      "description": ":garlic: 🧄"
    },
    {
      "name": ":gb:",
      "description": ":gb: 🇬🇧"
    },
    {
      "name": ":gear:",
      "description": ":gear: ⚙"
    },
    {
      "name": ":gem:",
      "description": ":gem: 💎"
    },
    {
      "name": ":gemini:",
      "description": ":gemini: ♊"
    },
    {
      "name": ":genie:",
      "description": ":genie: 🧞"
    },
    {
      "name": ":genie_man:",
      "description": ":genie_man: 🧞♂"
    },
    {
      "name": ":genie_woman:",
      "description": ":genie_woman: 🧞♀"
    },
    {
      "name": ":georgia:",
      "description": ":georgia: 🇬🇪"
    },
    {
      "name": ":ghana:",
      "description": ":ghana: 🇬🇭"
    },
    {
      "name": ":ghost:",
      "description": ":ghost: 👻"
    },
    {
      "name": ":gibraltar:",
      "description": ":gibraltar: 🇬🇮"
    },
    {
      "name": ":gift:",
      "description": ":gift: 🎁"
    },
    {
      "name": ":gift_heart:",
      "description": ":gift_heart: 💝"
    },
    {
      "name": ":giraffe:",
      "description": ":giraffe: 🦒"
    },
    {
      "name": ":girl:",
      "description": ":girl: 👧"
    },
    {
      "name": ":globe_with_meridians:",
      "description": ":globe_with_meridians: 🌐"
    },
    {
      "name": ":gloves:",
      "description": ":gloves: 🧤"
    },
    {
      "name": ":goal_net:",
      "description": ":goal_net: 🥅"
    },
    {
      "name": ":goat:",
      "description": ":goat: 🐐"
    },
    {
      "name": ":goggles:",
      "description": ":goggles: 🥽"
    },
    {
      "name": ":golf:",
      "description": ":golf: ⛳"
    },
    {
      "name": ":golfing:",
      "description": ":golfing: 🏌"
    },
    {
      "name": ":golfing_man:",
      "description": ":golfing_man: 🏌♂"
    },
    {
      "name": ":golfing_woman:",
      "description": ":golfing_woman: 🏌♀"
    },
    {
      "name": ":gorilla:",
      "description": ":gorilla: 🦍"
    },
    {
      "name": ":grapes:",
      "description": ":grapes: 🍇"
    },
    {
      "name": ":greece:",
      "description": ":greece: 🇬🇷"
    },
    {
      "name": ":green_apple:",
      "description": ":green_apple: 🍏"
    },
    {
      "name": ":green_book:",
      "description": ":green_book: 📗"
    },
    {
      "name": ":green_circle:",
      "description": ":green_circle: 🟢"
    },
    {
      "name": ":green_heart:",
      "description": ":green_heart: 💚"
    },
    {
      "name": ":green_salad:",
      "description": ":green_salad: 🥗"
    },
    {
      "name": ":green_square:",
      "description": ":green_square: 🟩"
    },
    {
      "name": ":greenland:",
      "description": ":greenland: 🇬🇱"
    },
    {
      "name": ":grenada:",
      "description": ":grenada: 🇬🇩"
    },
    {
      "name": ":grey_exclamation:",
      "description": ":grey_exclamation: ❕"
    },
    {
      "name": ":grey_question:",
      "description": ":grey_question: ❔"
    },
    {
      "name": ":grimacing:",
      "description": ":grimacing: 😬"
    },
    {
      "name": ":grin:",
      "description": ":grin: 😁"
    },
    {
      "name": ":grinning:",
      "description": ":grinning: 😀"
    },
    {
      "name": ":guadeloupe:",
      "description": ":guadeloupe: 🇬🇵"
    },
    {
      "name": ":guam:",
      "description": ":guam: 🇬🇺"
    },
    {
      "name": ":guard:",
      "description": ":guard: 💂"
    },
    {
      "name": ":guardsman:",
      "description": ":guardsman: 💂♂"
    },
    {
      "name": ":guardswoman:",
      "description": ":guardswoman: 💂♀"
    },
    {
      "name": ":guatemala:",
      "description": ":guatemala: 🇬🇹"
    },
    {
      "name": ":guernsey:",
      "description": ":guernsey: 🇬🇬"
    },
    {
      "name": ":guide_dog:",
      "description": ":guide_dog: 🦮"
    },
    {
      "name": ":guinea:",
      "description": ":guinea: 🇬🇳"
    },
    {
      "name": ":guinea_bissau:",
      "description": ":guinea_bissau: 🇬🇼"
    },
    {
      "name": ":guitar:",
      "description": ":guitar: 🎸"
    },
    {
      "name": ":gun:",
      "description": ":gun: 🔫"
    },
    {
      "name": ":guyana:",
      "description": ":guyana: 🇬🇾"
    },
    {
      "name": ":haircut:",
      "description": ":haircut: 💇"
    },
    {
      "name": ":haircut_man:",
      "description": ":haircut_man: 💇♂"
    },
    {
      "name": ":haircut_woman:",
      "description": ":haircut_woman: 💇♀"
    },
    {
      "name": ":haiti:",
      "description": ":haiti: 🇭🇹"
    },
    {
      "name": ":hamburger:",
      "description": ":hamburger: 🍔"
    },
    {
      "name": ":hammer:",
      "description": ":hammer: 🔨"
    },
    {
      "name": ":hammer_and_pick:",
      "description": ":hammer_and_pick: ⚒"
    },
    {
      "name": ":hammer_and_wrench:",
      "description": ":hammer_and_wrench: 🛠"
    },
    {
      "name": ":hamster:",
      "description": ":hamster: 🐹"
    },
    {
      "name": ":hand:",
      "description": ":hand: ✋"
    },
    {
      "name": ":hand_over_mouth:",
      "description": ":hand_over_mouth: 🤭"
    },
    {
      "name": ":handbag:",
      "description": ":handbag: 👜"
    },
    {
      "name": ":handball_person:",
      "description": ":handball_person: 🤾"
    },
    {
      "name": ":handshake:",
      "description": ":handshake: 🤝"
    },
    {
      "name": ":hankey:",
      "description": ":hankey: 💩"
    },
    {
      "name": ":hash:",
      "description": ":hash: #⃣"
    },
    {
      "name": ":hatched_chick:",
      "description": ":hatched_chick: 🐥"
    },
    {
      "name": ":hatching_chick:",
      "description": ":hatching_chick: 🐣"
    },
    {
      "name": ":headphones:",
      "description": ":headphones: 🎧"
    },
    {
      "name": ":headstone:",
      "description": ":headstone: 🪦"
    },
    {
      "name": ":health_worker:",
      "description": ":health_worker: 🧑⚕"
    },
    {
      "name": ":hear_no_evil:",
      "description": ":hear_no_evil: 🙉"
    },
    {
      "name": ":heard_mcdonald_islands:",
      "description": ":heard_mcdonald_islands: 🇭🇲"
    },
    {
      "name": ":heart:",
      "description": ":heart: ❤"
    },
    {
      "name": ":heart_decoration:",
      "description": ":heart_decoration: 💟"
    },
    {
      "name": ":heart_eyes:",
      "description": ":heart_eyes: 😍"
    },
    {
      "name": ":heart_eyes_cat:",
      "description": ":heart_eyes_cat: 😻"
    },
    {
      "name": ":heart_on_fire:",
      "description": ":heart_on_fire: ❤🔥"
    },
    {
      "name": ":heartbeat:",
      "description": ":heartbeat: 💓"
    },
    {
      "name": ":heartpulse:",
      "description": ":heartpulse: 💗"
    },
    {
      "name": ":hearts:",
      "description": ":hearts: ♥"
    },
    {
      "name": ":heavy_check_mark:",
      "description": ":heavy_check_mark: ✔"
    },
    {
      "name": ":heavy_division_sign:",
      "description": ":heavy_division_sign: ➗"
    },
    {
      "name": ":heavy_dollar_sign:",
      "description": ":heavy_dollar_sign: 💲"
    },
    {
      "name": ":heavy_exclamation_mark:",
      "description": ":heavy_exclamation_mark: ❗"
    },
    {
      "name": ":heavy_heart_exclamation:",
      "description": ":heavy_heart_exclamation: ❣"
    },
    {
      "name": ":heavy_minus_sign:",
      "description": ":heavy_minus_sign: ➖"
    },
    {
      "name": ":heavy_multiplication_x:",
      "description": ":heavy_multiplication_x: ✖"
    },
    {
      "name": ":heavy_plus_sign:",
      "description": ":heavy_plus_sign: ➕"
    },
    {
      "name": ":hedgehog:",
      "description": ":hedgehog: 🦔"
    },
    {
      "name": ":helicopter:",
      "description": ":helicopter: 🚁"
    },
    {
      "name": ":herb:",
      "description": ":herb: 🌿"
    },
    {
      "name": ":hibiscus:",
      "description": ":hibiscus: 🌺"
    },
    {
      "name": ":high_brightness:",
      "description": ":high_brightness: 🔆"
    },
    {
      "name": ":high_heel:",
      "description": ":high_heel: 👠"
    },
    {
      "name": ":hiking_boot:",
      "description": ":hiking_boot: 🥾"
    },
    {
      "name": ":hindu_temple:",
      "description": ":hindu_temple: 🛕"
    },
    {
      "name": ":hippopotamus:",
      "description": ":hippopotamus: 🦛"
    },
    {
      "name": ":hocho:",
      "description": ":hocho: 🔪"
    },
    {
      "name": ":hole:",
      "description": ":hole: 🕳"
    },
    {
      "name": ":honduras:",
      "description": ":honduras: 🇭🇳"
    },
    {
      "name": ":honey_pot:",
      "description": ":honey_pot: 🍯"
    },
    {
      "name": ":honeybee:",
      "description": ":honeybee: 🐝"
    },
    {
      "name": ":hong_kong:",
      "description": ":hong_kong: 🇭🇰"
    },
    {
      "name": ":hook:",
      "description": ":hook: 🪝"
    },
    {
      "name": ":horse:",
      "description": ":horse: 🐴"
    },
    {
      "name": ":horse_racing:",
      "description": ":horse_racing: 🏇"
    },
    {
      "name": ":hospital:",
      "description": ":hospital: 🏥"
    },
    {
      "name": ":hot_face:",
      "description": ":hot_face: 🥵"
    },
    {
      "name": ":hot_pepper:",
      "description": ":hot_pepper: 🌶"
    },
    {
      "name": ":hotdog:",
      "description": ":hotdog: 🌭"
    },
    {
      "name": ":hotel:",
      "description": ":hotel: 🏨"
    },
    {
      "name": ":hotsprings:",
      "description": ":hotsprings: ♨"
    },
    {
      "name": ":hourglass:",
      "description": ":hourglass: ⌛"
    },
    {
      "name": ":hourglass_flowing_sand:",
      "description": ":hourglass_flowing_sand: ⏳"
    },
    {
      "name": ":house:",
      "description": ":house: 🏠"
    },
    {
      "name": ":house_with_garden:",
      "description": ":house_with_garden: 🏡"
    },
    {
      "name": ":houses:",
      "description": ":houses: 🏘"
    },
    {
      "name": ":hugs:",
      "description": ":hugs: 🤗"
    },
    {
      "name": ":hungary:",
      "description": ":hungary: 🇭🇺"
    },
    {
      "name": ":hushed:",
      "description": ":hushed: 😯"
    },
    {
      "name": ":hut:",
      "description": ":hut: 🛖"
    },
    {
      "name": ":ice_cream:",
      "description": ":ice_cream: 🍨"
    },
    {
      "name": ":ice_cube:",
      "description": ":ice_cube: 🧊"
    },
    {
      "name": ":ice_hockey:",
      "description": ":ice_hockey: 🏒"
    },
    {
      "name": ":ice_skate:",
      "description": ":ice_skate: ⛸"
    },
    {
      "name": ":icecream:",
      "description": ":icecream: 🍦"
    },
    {
      "name": ":iceland:",
      "description": ":iceland: 🇮🇸"
    },
    {
      "name": ":id:",
      "description": ":id: 🆔"
    },
    {
      "name": ":ideograph_advantage:",
      "description": ":ideograph_advantage: 🉐"
    },
    {
      "name": ":imp:",
      "description": ":imp: 👿"
    },
    {
      "name": ":inbox_tray:",
      "description": ":inbox_tray: 📥"
    },
    {
      "name": ":incoming_envelope:",
      "description": ":incoming_envelope: 📨"
    },
    {
      "name": ":india:",
      "description": ":india: 🇮🇳"
    },
    {
      "name": ":indonesia:",
      "description": ":indonesia: 🇮🇩"
    },
    {
      "name": ":infinity:",
      "description": ":infinity: ♾"
    },
    {
      "name": ":information_desk_person:",
      "description": ":information_desk_person: 💁"
    },
    {
      "name": ":information_source:",
      "description": ":information_source: ℹ"
    },
    {
      "name": ":innocent:",
      "description": ":innocent: 😇"
    },
    {
      "name": ":interrobang:",
      "description": ":interrobang: ⁉"
    },
    {
      "name": ":iphone:",
      "description": ":iphone: 📱"
    },
    {
      "name": ":iran:",
      "description": ":iran: 🇮🇷"
    },
    {
      "name": ":iraq:",
      "description": ":iraq: 🇮🇶"
    },
    {
      "name": ":ireland:",
      "description": ":ireland: 🇮🇪"
    },
    {
      "name": ":isle_of_man:",
      "description": ":isle_of_man: 🇮🇲"
    },
    {
      "name": ":israel:",
      "description": ":israel: 🇮🇱"
    },
    {
      "name": ":it:",
      "description": ":it: 🇮🇹"
    },
    {
      "name": ":izakaya_lantern:",
      "description": ":izakaya_lantern: 🏮"
    },
    {
      "name": ":jack_o_lantern:",
      "description": ":jack_o_lantern: 🎃"
    },
    {
      "name": ":jamaica:",
      "description": ":jamaica: 🇯🇲"
    },
    {
      "name": ":japan:",
      "description": ":japan: 🗾"
    },
    {
      "name": ":japanese_castle:",
      "description": ":japanese_castle: 🏯"
    },
    {
      "name": ":japanese_goblin:",
      "description": ":japanese_goblin: 👺"
    },
    {
      "name": ":japanese_ogre:",
      "description": ":japanese_ogre: 👹"
    },
    {
      "name": ":jeans:",
      "description": ":jeans: 👖"
    },
    {
      "name": ":jersey:",
      "description": ":jersey: 🇯🇪"
    },
    {
      "name": ":jigsaw:",
      "description": ":jigsaw: 🧩"
    },
    {
      "name": ":jordan:",
      "description": ":jordan: 🇯🇴"
    },
    {
      "name": ":joy:",
      "description": ":joy: 😂"
    },
    {
      "name": ":joy_cat:",
      "description": ":joy_cat: 😹"
    },
    {
      "name": ":joystick:",
      "description": ":joystick: 🕹"
    },
    {
      "name": ":jp:",
      "description": ":jp: 🇯🇵"
    },
    {
      "name": ":judge:",
      "description": ":judge: 🧑⚖"
    },
    {
      "name": ":juggling_person:",
      "description": ":juggling_person: 🤹"
    },
    {
      "name": ":kaaba:",
      "description": ":kaaba: 🕋"
    },
    {
      "name": ":kangaroo:",
      "description": ":kangaroo: 🦘"
    },
    {
      "name": ":kazakhstan:",
      "description": ":kazakhstan: 🇰🇿"
    },
    {
      "name": ":kenya:",
      "description": ":kenya: 🇰🇪"
    },
    {
      "name": ":key:",
      "description": ":key: 🔑"
    },
    {
      "name": ":keyboard:",
      "description": ":keyboard: ⌨"
    },
    {
      "name": ":keycap_ten:",
      "description": ":keycap_ten: 🔟"
    },
    {
      "name": ":kick_scooter:",
      "description": ":kick_scooter: 🛴"
    },
    {
      "name": ":kimono:",
      "description": ":kimono: 👘"
    },
    {
      "name": ":kiribati:",
      "description": ":kiribati: 🇰🇮"
    },
    {
      "name": ":kiss:",
      "description": ":kiss: 💋"
    },
    {
      "name": ":kissing:",
      "description": ":kissing: 😗"
    },
    {
      "name": ":kissing_cat:",
      "description": ":kissing_cat: 😽"
    },
    {
      "name": ":kissing_closed_eyes:",
      "description": ":kissing_closed_eyes: 😚"
    },
    {
      "name": ":kissing_heart:",
      "description": ":kissing_heart: 😘"
    },
    {
      "name": ":kissing_smiling_eyes:",
      "description": ":kissing_smiling_eyes: 😙"
    },
    {
      "name": ":kite:",
      "description": ":kite: 🪁"
    },
    {
      "name": ":kiwi_fruit:",
      "description": ":kiwi_fruit: 🥝"
    },
    {
      "name": ":kneeling_man:",
      "description": ":kneeling_man: 🧎♂"
    },
    {
      "name": ":kneeling_person:",
      "description": ":kneeling_person: 🧎"
    },
    {
      "name": ":kneeling_woman:",
      "description": ":kneeling_woman: 🧎♀"
    },
    {
      "name": ":knife:",
      "description": ":knife: 🔪"
    },
    {
      "name": ":knot:",
      "description": ":knot: 🪢"
    },
    {
      "name": ":koala:",
      "description": ":koala: 🐨"
    },
    {
      "name": ":koko:",
      "description": ":koko: 🈁"
    },
    {
      "name": ":kosovo:",
      "description": ":kosovo: 🇽🇰"
    },
    {
      "name": ":kr:",
      "description": ":kr: 🇰🇷"
    },
    {
      "name": ":kuwait:",
      "description": ":kuwait: 🇰🇼"
    },
    {
      "name": ":kyrgyzstan:",
      "description": ":kyrgyzstan: 🇰🇬"
    },
    {
      "name": ":lab_coat:",
      "description": ":lab_coat: 🥼"
    },
    {
      "name": ":label:",
      "description": ":label: 🏷"
    },
    {
      "name": ":lacrosse:",
      "description": ":lacrosse: 🥍"
    },
    {
      "name": ":ladder:",
      "description": ":ladder: 🪜"
    },
    {
      "name": ":lady_beetle:",
      "description": ":lady_beetle: 🐞"
    },
    {
      "name": ":lantern:",
      "description": ":lantern: 🏮"
    },
    {
      "name": ":laos:",
      "description": ":laos: 🇱🇦"
    },
    {
      "name": ":large_blue_circle:",
      "description": ":large_blue_circle: 🔵"
    },
    {
      "name": ":large_blue_diamond:",
      "description": ":large_blue_diamond: 🔷"
    },
    {
      "name": ":large_orange_diamond:",
      "description": ":large_orange_diamond: 🔶"
    },
    {
      "name": ":last_quarter_moon:",
      "description": ":last_quarter_moon: 🌗"
    },
    {
      "name": ":last_quarter_moon_with_face:",
      "description": ":last_quarter_moon_with_face: 🌜"
    },
    {
      "name": ":latin_cross:",
      "description": ":latin_cross: ✝"
    },
    {
      "name": ":latvia:",
      "description": ":latvia: 🇱🇻"
    },
    {
      "name": ":laughing:",
      "description": ":laughing: 😆"
    },
    {
      "name": ":leafy_green:",
      "description": ":leafy_green: 🥬"
    },
    {
      "name": ":leaves:",
      "description": ":leaves: 🍃"
    },
    {
      "name": ":lebanon:",
      "description": ":lebanon: 🇱🇧"
    },
    {
      "name": ":ledger:",
      "description": ":ledger: 📒"
    },
    {
      "name": ":left_luggage:",
      "description": ":left_luggage: 🛅"
    },
    {
      "name": ":left_right_arrow:",
      "description": ":left_right_arrow: ↔"
    },
    {
      "name": ":left_speech_bubble:",
      "description": ":left_speech_bubble: 🗨"
    },
    {
      "name": ":leftwards_arrow_with_hook:",
      "description": ":leftwards_arrow_with_hook: ↩"
    },
    {
      "name": ":leg:",
      "description": ":leg: 🦵"
    },
    {
      "name": ":lemon:",
      "description": ":lemon: 🍋"
    },
    {
      "name": ":leo:",
      "description": ":leo: ♌"
    },
    {
      "name": ":leopard:",
      "description": ":leopard: 🐆"
    },
    {
      "name": ":lesotho:",
      "description": ":lesotho: 🇱🇸"
    },
    {
      "name": ":level_slider:",
      "description": ":level_slider: 🎚"
    },
    {
      "name": ":liberia:",
      "description": ":liberia: 🇱🇷"
    },
    {
      "name": ":libra:",
      "description": ":libra: ♎"
    },
    {
      "name": ":libya:",
      "description": ":libya: 🇱🇾"
    },
    {
      "name": ":liechtenstein:",
      "description": ":liechtenstein: 🇱🇮"
    },
    {
      "name": ":light_rail:",
      "description": ":light_rail: 🚈"
    },
    {
      "name": ":link:",
      "description": ":link: 🔗"
    },
    {
      "name": ":lion:",
      "description": ":lion: 🦁"
    },
    {
      "name": ":lips:",
      "description": ":lips: 👄"
    },
    {
      "name": ":lipstick:",
      "description": ":lipstick: 💄"
    },
    {
      "name": ":lithuania:",
      "description": ":lithuania: 🇱🇹"
    },
    {
      "name": ":lizard:",
      "description": ":lizard: 🦎"
    },
    {
      "name": ":llama:",
      "description": ":llama: 🦙"
    },
    {
      "name": ":lobster:",
      "description": ":lobster: 🦞"
    },
    {
      "name": ":lock:",
      "description": ":lock: 🔒"
    },
    {
      "name": ":lock_with_ink_pen:",
      "description": ":lock_with_ink_pen: 🔏"
    },
    {
      "name": ":lollipop:",
      "description": ":lollipop: 🍭"
    },
    {
      "name": ":long_drum:",
      "description": ":long_drum: 🪘"
    },
    {
      "name": ":loop:",
      "description": ":loop: ➿"
    },
    {
      "name": ":lotion_bottle:",
      "description": ":lotion_bottle: 🧴"
    },
    {
      "name": ":lotus_position:",
      "description": ":lotus_position: 🧘"
    },
    {
      "name": ":lotus_position_man:",
      "description": ":lotus_position_man: 🧘♂"
    },
    {
      "name": ":lotus_position_woman:",
      "description": ":lotus_position_woman: 🧘♀"
    },
    {
      "name": ":loud_sound:",
      "description": ":loud_sound: 🔊"
    },
    {
      "name": ":loudspeaker:",
      "description": ":loudspeaker: 📢"
    },
    {
      "name": ":love_hotel:",
      "description": ":love_hotel: 🏩"
    },
    {
      "name": ":love_letter:",
      "description": ":love_letter: 💌"
    },
    {
      "name": ":love_you_gesture:",
      "description": ":love_you_gesture: 🤟"
    },
    {
      "name": ":low_brightness:",
      "description": ":low_brightness: 🔅"
    },
    {
      "name": ":luggage:",
      "description": ":luggage: 🧳"
    },
    {
      "name": ":lungs:",
      "description": ":lungs: 🫁"
    },
    {
      "name": ":luxembourg:",
      "description": ":luxembourg: 🇱🇺"
    },
    {
      "name": ":lying_face:",
      "description": ":lying_face: 🤥"
    },
    {
      "name": ":m:",
      "description": ":m: Ⓜ"
    },
    {
      "name": ":macau:",
      "description": ":macau: 🇲🇴"
    },
    {
      "name": ":macedonia:",
      "description": ":macedonia: 🇲🇰"
    },
    {
      "name": ":madagascar:",
      "description": ":madagascar: 🇲🇬"
    },
    {
      "name": ":mag:",
      "description": ":mag: 🔍"
    },
    {
      "name": ":mag_right:",
      "description": ":mag_right: 🔎"
    },
    {
      "name": ":mage:",
      "description": ":mage: 🧙"
    },
    {
      "name": ":mage_man:",
      "description": ":mage_man: 🧙♂"
    },
    {
      "name": ":mage_woman:",
      "description": ":mage_woman: 🧙♀"
    },
    {
      "name": ":magic_wand:",
      "description": ":magic_wand: 🪄"
    },
    {
      "name": ":magnet:",
      "description": ":magnet: 🧲"
    },
    {
      "name": ":mahjong:",
      "description": ":mahjong: 🀄"
    },
    {
      "name": ":mailbox:",
      "description": ":mailbox: 📫"
    },
    {
      "name": ":mailbox_closed:",
      "description": ":mailbox_closed: 📪"
    },
    {
      "name": ":mailbox_with_mail:",
      "description": ":mailbox_with_mail: 📬"
    },
    {
      "name": ":mailbox_with_no_mail:",
      "description": ":mailbox_with_no_mail: 📭"
    },
    {
      "name": ":malawi:",
      "description": ":malawi: 🇲🇼"
    },
    {
      "name": ":malaysia:",
      "description": ":malaysia: 🇲🇾"
    },
    {
      "name": ":maldives:",
      "description": ":maldives: 🇲🇻"
    },
    {
      "name": ":male_detective:",
      "description": ":male_detective: 🕵♂"
    },
    {
      "name": ":male_sign:",
      "description": ":male_sign: ♂"
    },
    {
      "name": ":mali:",
      "description": ":mali: 🇲🇱"
    },
    {
      "name": ":malta:",
      "description": ":malta: 🇲🇹"
    },
    {
      "name": ":mammoth:",
      "description": ":mammoth: 🦣"
    },
    {
      "name": ":man:",
      "description": ":man: 👨"
    },
    {
      "name": ":man_artist:",
      "description": ":man_artist: 👨🎨"
    },
    {
      "name": ":man_astronaut:",
      "description": ":man_astronaut: 👨🚀"
    },
    {
      "name": ":man_beard:",
      "description": ":man_beard: 🧔♂"
    },
    {
      "name": ":man_cartwheeling:",
      "description": ":man_cartwheeling: 🤸♂"
    },
    {
      "name": ":man_cook:",
      "description": ":man_cook: 👨🍳"
    },
    {
      "name": ":man_dancing:",
      "description": ":man_dancing: 🕺"
    },
    {
      "name": ":man_facepalming:",
      "description": ":man_facepalming: 🤦♂"
    },
    {
      "name": ":man_factory_worker:",
      "description": ":man_factory_worker: 👨🏭"
    },
    {
      "name": ":man_farmer:",
      "description": ":man_farmer: 👨🌾"
    },
    {
      "name": ":man_feeding_baby:",
      "description": ":man_feeding_baby: 👨🍼"
    },
    {
      "name": ":man_firefighter:",
      "description": ":man_firefighter: 👨🚒"
    },
    {
      "name": ":man_health_worker:",
      "description": ":man_health_worker: 👨⚕"
    },
    {
      "name": ":man_in_manual_wheelchair:",
      "description": ":man_in_manual_wheelchair: 👨🦽"
    },
    {
      "name": ":man_in_motorized_wheelchair:",
      "description": ":man_in_motorized_wheelchair: 👨🦼"
    },
    {
      "name": ":man_in_tuxedo:",
      "description": ":man_in_tuxedo: 🤵♂"
    },
    {
      "name": ":man_judge:",
      "description": ":man_judge: 👨⚖"
    },
    {
      "name": ":man_juggling:",
      "description": ":man_juggling: 🤹♂"
    },
    {
      "name": ":man_mechanic:",
      "description": ":man_mechanic: 👨🔧"
    },
    {
      "name": ":man_office_worker:",
      "description": ":man_office_worker: 👨💼"
    },
    {
      "name": ":man_pilot:",
      "description": ":man_pilot: 👨✈"
    },
    {
      "name": ":man_playing_handball:",
      "description": ":man_playing_handball: 🤾♂"
    },
    {
      "name": ":man_playing_water_polo:",
      "description": ":man_playing_water_polo: 🤽♂"
    },
    {
      "name": ":man_scientist:",
      "description": ":man_scientist: 👨🔬"
    },
    {
      "name": ":man_shrugging:",
      "description": ":man_shrugging: 🤷♂"
    },
    {
      "name": ":man_singer:",
      "description": ":man_singer: 👨🎤"
    },
    {
      "name": ":man_student:",
      "description": ":man_student: 👨🎓"
    },
    {
      "name": ":man_teacher:",
      "description": ":man_teacher: 👨🏫"
    },
    {
      "name": ":man_technologist:",
      "description": ":man_technologist: 👨💻"
    },
    {
      "name": ":man_with_gua_pi_mao:",
      "description": ":man_with_gua_pi_mao: 👲"
    },
    {
      "name": ":man_with_probing_cane:",
      "description": ":man_with_probing_cane: 👨🦯"
    },
    {
      "name": ":man_with_turban:",
      "description": ":man_with_turban: 👳♂"
    },
    {
      "name": ":man_with_veil:",
      "description": ":man_with_veil: 👰♂"
    },
    {
      "name": ":mandarin:",
      "description": ":mandarin: 🍊"
    },
    {
      "name": ":mango:",
      "description": ":mango: 🥭"
    },
    {
      "name": ":mans_shoe:",
      "description": ":mans_shoe: 👞"
    },
    {
      "name": ":mantelpiece_clock:",
      "description": ":mantelpiece_clock: 🕰"
    },
    {
      "name": ":manual_wheelchair:",
      "description": ":manual_wheelchair: 🦽"
    },
    {
      "name": ":maple_leaf:",
      "description": ":maple_leaf: 🍁"
    },
    {
      "name": ":marshall_islands:",
      "description": ":marshall_islands: 🇲🇭"
    },
    {
      "name": ":martial_arts_uniform:",
      "description": ":martial_arts_uniform: 🥋"
    },
    {
      "name": ":martinique:",
      "description": ":martinique: 🇲🇶"
    },
    {
      "name": ":mask:",
      "description": ":mask: 😷"
    },
    {
      "name": ":massage:",
      "description": ":massage: 💆"
    },
    {
      "name": ":massage_man:",
      "description": ":massage_man: 💆♂"
    },
    {
      "name": ":massage_woman:",
      "description": ":massage_woman: 💆♀"
    },
    {
      "name": ":mate:",
      "description": ":mate: 🧉"
    },
    {
      "name": ":mauritania:",
      "description": ":mauritania: 🇲🇷"
    },
    {
      "name": ":mauritius:",
      "description": ":mauritius: 🇲🇺"
    },
    {
      "name": ":mayotte:",
      "description": ":mayotte: 🇾🇹"
    },
    {
      "name": ":meat_on_bone:",
      "description": ":meat_on_bone: 🍖"
    },
    {
      "name": ":mechanic:",
      "description": ":mechanic: 🧑🔧"
    },
    {
      "name": ":mechanical_arm:",
      "description": ":mechanical_arm: 🦾"
    },
    {
      "name": ":mechanical_leg:",
      "description": ":mechanical_leg: 🦿"
    },
    {
      "name": ":medal_military:",
      "description": ":medal_military: 🎖"
    },
    {
      "name": ":medal_sports:",
      "description": ":medal_sports: 🏅"
    },
    {
      "name": ":medical_symbol:",
      "description": ":medical_symbol: ⚕"
    },
    {
      "name": ":mega:",
      "description": ":mega: 📣"
    },
    {
      "name": ":melon:",
      "description": ":melon: 🍈"
    },
    {
      "name": ":memo:",
      "description": ":memo: 📝"
    },
    {
      "name": ":men_wrestling:",
      "description": ":men_wrestling: 🤼♂"
    },
    {
      "name": ":mending_heart:",
      "description": ":mending_heart: ❤🩹"
    },
    {
      "name": ":menorah:",
      "description": ":menorah: 🕎"
    },
    {
      "name": ":mens:",
      "description": ":mens: 🚹"
    },
    {
      "name": ":mermaid:",
      "description": ":mermaid: 🧜♀"
    },
    {
      "name": ":merman:",
      "description": ":merman: 🧜♂"
    },
    {
      "name": ":merperson:",
      "description": ":merperson: 🧜"
    },
    {
      "name": ":metal:",
      "description": ":metal: 🤘"
    },
    {
      "name": ":metro:",
      "description": ":metro: 🚇"
    },
    {
      "name": ":mexico:",
      "description": ":mexico: 🇲🇽"
    },
    {
      "name": ":microbe:",
      "description": ":microbe: 🦠"
    },
    {
      "name": ":micronesia:",
      "description": ":micronesia: 🇫🇲"
    },
    {
      "name": ":microphone:",
      "description": ":microphone: 🎤"
    },
    {
      "name": ":microscope:",
      "description": ":microscope: 🔬"
    },
    {
      "name": ":middle_finger:",
      "description": ":middle_finger: 🖕"
    },
    {
      "name": ":military_helmet:",
      "description": ":military_helmet: 🪖"
    },
    {
      "name": ":milk_glass:",
      "description": ":milk_glass: 🥛"
    },
    {
      "name": ":milky_way:",
      "description": ":milky_way: 🌌"
    },
    {
      "name": ":minibus:",
      "description": ":minibus: 🚐"
    },
    {
      "name": ":minidisc:",
      "description": ":minidisc: 💽"
    },
    {
      "name": ":mirror:",
      "description": ":mirror: 🪞"
    },
    {
      "name": ":mobile_phone_off:",
      "description": ":mobile_phone_off: 📴"
    },
    {
      "name": ":moldova:",
      "description": ":moldova: 🇲🇩"
    },
    {
      "name": ":monaco:",
      "description": ":monaco: 🇲🇨"
    },
    {
      "name": ":money_mouth_face:",
      "description": ":money_mouth_face: 🤑"
    },
    {
      "name": ":money_with_wings:",
      "description": ":money_with_wings: 💸"
    },
    {
      "name": ":moneybag:",
      "description": ":moneybag: 💰"
    },
    {
      "name": ":mongolia:",
      "description": ":mongolia: 🇲🇳"
    },
    {
      "name": ":monkey:",
      "description": ":monkey: 🐒"
    },
    {
      "name": ":monkey_face:",
      "description": ":monkey_face: 🐵"
    },
    {
      "name": ":monocle_face:",
      "description": ":monocle_face: 🧐"
    },
    {
      "name": ":monorail:",
      "description": ":monorail: 🚝"
    },
    {
      "name": ":montenegro:",
      "description": ":montenegro: 🇲🇪"
    },
    {
      "name": ":montserrat:",
      "description": ":montserrat: 🇲🇸"
    },
    {
      "name": ":moon:",
      "description": ":moon: 🌔"
    },
    {
      "name": ":moon_cake:",
      "description": ":moon_cake: 🥮"
    },
    {
      "name": ":morocco:",
      "description": ":morocco: 🇲🇦"
    },
    {
      "name": ":mortar_board:",
      "description": ":mortar_board: 🎓"
    },
    {
      "name": ":mosque:",
      "description": ":mosque: 🕌"
    },
    {
      "name": ":mosquito:",
      "description": ":mosquito: 🦟"
    },
    {
      "name": ":motor_boat:",
      "description": ":motor_boat: 🛥"
    },
    {
      "name": ":motor_scooter:",
      "description": ":motor_scooter: 🛵"
    },
    {
      "name": ":motorcycle:",
      "description": ":motorcycle: 🏍"
    },
    {
      "name": ":motorized_wheelchair:",
      "description": ":motorized_wheelchair: 🦼"
    },
    {
      "name": ":motorway:",
      "description": ":motorway: 🛣"
    },
    {
      "name": ":mount_fuji:",
      "description": ":mount_fuji: 🗻"
    },
    {
      "name": ":mountain:",
      "description": ":mountain: ⛰"
    },
    {
      "name": ":mountain_bicyclist:",
      "description": ":mountain_bicyclist: 🚵"
    },
    {
      "name": ":mountain_biking_man:",
      "description": ":mountain_biking_man: 🚵♂"
    },
    {
      "name": ":mountain_biking_woman:",
      "description": ":mountain_biking_woman: 🚵♀"
    },
    {
      "name": ":mountain_cableway:",
      "description": ":mountain_cableway: 🚠"
    },
    {
      "name": ":mountain_railway:",
      "description": ":mountain_railway: 🚞"
    },
    {
      "name": ":mountain_snow:",
      "description": ":mountain_snow: 🏔"
    },
    {
      "name": ":mouse:",
      "description": ":mouse: 🐭"
    },
    {
      "name": ":mouse2:",
      "description": ":mouse2: 🐁"
    },
    {
      "name": ":mouse_trap:",
      "description": ":mouse_trap: 🪤"
    },
    {
      "name": ":movie_camera:",
      "description": ":movie_camera: 🎥"
    },
    {
      "name": ":moyai:",
      "description": ":moyai: 🗿"
    },
    {
      "name": ":mozambique:",
      "description": ":mozambique: 🇲🇿"
    },
    {
      "name": ":mrs_claus:",
      "description": ":mrs_claus: 🤶"
    },
    {
      "name": ":muscle:",
      "description": ":muscle: 💪"
    },
    {
      "name": ":mushroom:",
      "description": ":mushroom: 🍄"
    },
    {
      "name": ":musical_keyboard:",
      "description": ":musical_keyboard: 🎹"
    },
    {
      "name": ":musical_note:",
      "description": ":musical_note: 🎵"
    },
    {
      "name": ":musical_score:",
      "description": ":musical_score: 🎼"
    },
    {
      "name": ":mute:",
      "description": ":mute: 🔇"
    },
    {
      "name": ":mx_claus:",
      "description": ":mx_claus: 🧑🎄"
    },
    {
      "name": ":myanmar:",
      "description": ":myanmar: 🇲🇲"
    },
    {
      "name": ":nail_care:",
      "description": ":nail_care: 💅"
    },
    {
      "name": ":name_badge:",
      "description": ":name_badge: 📛"
    },
    {
      "name": ":namibia:",
      "description": ":namibia: 🇳🇦"
    },
    {
      "name": ":national_park:",
      "description": ":national_park: 🏞"
    },
    {
      "name": ":nauru:",
      "description": ":nauru: 🇳🇷"
    },
    {
      "name": ":nauseated_face:",
      "description": ":nauseated_face: 🤢"
    },
    {
      "name": ":nazar_amulet:",
      "description": ":nazar_amulet: 🧿"
    },
    {
      "name": ":necktie:",
      "description": ":necktie: 👔"
    },
    {
      "name": ":negative_squared_cross_mark:",
      "description": ":negative_squared_cross_mark: ❎"
    },
    {
      "name": ":nepal:",
      "description": ":nepal: 🇳🇵"
    },
    {
      "name": ":nerd_face:",
      "description": ":nerd_face: 🤓"
    },
    {
      "name": ":nesting_dolls:",
      "description": ":nesting_dolls: 🪆"
    },
    {
      "name": ":netherlands:",
      "description": ":netherlands: 🇳🇱"
    },
    {
      "name": ":neutral_face:",
      "description": ":neutral_face: 😐"
    },
    {
      "name": ":new:",
      "description": ":new: 🆕"
    },
    {
      "name": ":new_caledonia:",
      "description": ":new_caledonia: 🇳🇨"
    },
    {
      "name": ":new_moon:",
      "description": ":new_moon: 🌑"
    },
    {
      "name": ":new_moon_with_face:",
      "description": ":new_moon_with_face: 🌚"
    },
    {
      "name": ":new_zealand:",
      "description": ":new_zealand: 🇳🇿"
    },
    {
      "name": ":newspaper:",
      "description": ":newspaper: 📰"
    },
    {
      "name": ":newspaper_roll:",
      "description": ":newspaper_roll: 🗞"
    },
    {
      "name": ":next_track_button:",
      "description": ":next_track_button: ⏭"
    },
    {
      "name": ":ng:",
      "description": ":ng: 🆖"
    },
    {
      "name": ":ng_man:",
      "description": ":ng_man: 🙅♂"
    },
    {
      "name": ":ng_woman:",
      "description": ":ng_woman: 🙅♀"
    },
    {
      "name": ":nicaragua:",
      "description": ":nicaragua: 🇳🇮"
    },
    {
      "name": ":niger:",
      "description": ":niger: 🇳🇪"
    },
    {
      "name": ":nigeria:",
      "description": ":nigeria: 🇳🇬"
    },
    {
      "name": ":night_with_stars:",
      "description": ":night_with_stars: 🌃"
    },
    {
      "name": ":nine:",
      "description": ":nine: 9⃣"
    },
    {
      "name": ":ninja:",
      "description": ":ninja: 🥷"
    },
    {
      "name": ":niue:",
      "description": ":niue: 🇳🇺"
    },
    {
      "name": ":no_bell:",
      "description": ":no_bell: 🔕"
    },
    {
      "name": ":no_bicycles:",
      "description": ":no_bicycles: 🚳"
    },
    {
      "name": ":no_entry:",
      "description": ":no_entry: ⛔"
    },
    {
      "name": ":no_entry_sign:",
      "description": ":no_entry_sign: 🚫"
    },
    {
      "name": ":no_good:",
      "description": ":no_good: 🙅"
    },
    {
      "name": ":no_good_man:",
      "description": ":no_good_man: 🙅♂"
    },
    {
      "name": ":no_good_woman:",
      "description": ":no_good_woman: 🙅♀"
    },
    {
      "name": ":no_mobile_phones:",
      "description": ":no_mobile_phones: 📵"
    },
    {
      "name": ":no_mouth:",
      "description": ":no_mouth: 😶"
    },
    {
      "name": ":no_pedestrians:",
      "description": ":no_pedestrians: 🚷"
    },
    {
      "name": ":no_smoking:",
      "description": ":no_smoking: 🚭"
    },
    {
      "name": ":non-potable_water:",
      "description": ":non-potable_water: 🚱"
    },
    {
      "name": ":norfolk_island:",
      "description": ":norfolk_island: 🇳🇫"
    },
    {
      "name": ":north_korea:",
      "description": ":north_korea: 🇰🇵"
    },
    {
      "name": ":northern_mariana_islands:",
      "description": ":northern_mariana_islands: 🇲🇵"
    },
    {
      "name": ":norway:",
      "description": ":norway: 🇳🇴"
    },
    {
      "name": ":nose:",
      "description": ":nose: 👃"
    },
    {
      "name": ":notebook:",
      "description": ":notebook: 📓"
    },
    {
      "name": ":notebook_with_decorative_cover:",
      "description": ":notebook_with_decorative_cover: 📔"
    },
    {
      "name": ":notes:",
      "description": ":notes: 🎶"
    },
    {
      "name": ":nut_and_bolt:",
      "description": ":nut_and_bolt: 🔩"
    },
    {
      "name": ":o:",
      "description": ":o: ⭕"
    },
    {
      "name": ":o2:",
      "description": ":o2: 🅾"
    },
    {
      "name": ":ocean:",
      "description": ":ocean: 🌊"
    },
    {
      "name": ":octopus:",
      "description": ":octopus: 🐙"
    },
    {
      "name": ":oden:",
      "description": ":oden: 🍢"
    },
    {
      "name": ":office:",
      "description": ":office: 🏢"
    },
    {
      "name": ":office_worker:",
      "description": ":office_worker: 🧑💼"
    },
    {
      "name": ":oil_drum:",
      "description": ":oil_drum: 🛢"
    },
    {
      "name": ":ok:",
      "description": ":ok: 🆗"
    },
    {
      "name": ":ok_hand:",
      "description": ":ok_hand: 👌"
    },
    {
      "name": ":ok_man:",
      "description": ":ok_man: 🙆♂"
    },
    {
      "name": ":ok_person:",
      "description": ":ok_person: 🙆"
    },
    {
      "name": ":ok_woman:",
      "description": ":ok_woman: 🙆♀"
    },
    {
      "name": ":old_key:",
      "description": ":old_key: 🗝"
    },
    {
      "name": ":older_adult:",
      "description": ":older_adult: 🧓"
    },
    {
      "name": ":older_man:",
      "description": ":older_man: 👴"
    },
    {
      "name": ":older_woman:",
      "description": ":older_woman: 👵"
    },
    {
      "name": ":olive:",
      "description": ":olive: 🫒"
    },
    {
      "name": ":om:",
      "description": ":om: 🕉"
    },
    {
      "name": ":oman:",
      "description": ":oman: 🇴🇲"
    },
    {
      "name": ":on:",
      "description": ":on: 🔛"
    },
    {
      "name": ":oncoming_automobile:",
      "description": ":oncoming_automobile: 🚘"
    },
    {
      "name": ":oncoming_bus:",
      "description": ":oncoming_bus: 🚍"
    },
    {
      "name": ":oncoming_police_car:",
      "description": ":oncoming_police_car: 🚔"
    },
    {
      "name": ":oncoming_taxi:",
      "description": ":oncoming_taxi: 🚖"
    },
    {
      "name": ":one:",
      "description": ":one: 1⃣"
    },
    {
      "name": ":one_piece_swimsuit:",
      "description": ":one_piece_swimsuit: 🩱"
    },
    {
      "name": ":onion:",
      "description": ":onion: 🧅"
    },
    {
      "name": ":open_book:",
      "description": ":open_book: 📖"
    },
    {
      "name": ":open_file_folder:",
      "description": ":open_file_folder: 📂"
    },
    {
      "name": ":open_hands:",
      "description": ":open_hands: 👐"
    },
    {
      "name": ":open_mouth:",
      "description": ":open_mouth: 😮"
    },
    {
      "name": ":open_umbrella:",
      "description": ":open_umbrella: ☂"
    },
    {
      "name": ":ophiuchus:",
      "description": ":ophiuchus: ⛎"
    },
    {
      "name": ":orange:",
      "description": ":orange: 🍊"
    },
    {
      "name": ":orange_book:",
      "description": ":orange_book: 📙"
    },
    {
      "name": ":orange_circle:",
      "description": ":orange_circle: 🟠"
    },
    {
      "name": ":orange_heart:",
      "description": ":orange_heart: 🧡"
    },
    {
      "name": ":orange_square:",
      "description": ":orange_square: 🟧"
    },
    {
      "name": ":orangutan:",
      "description": ":orangutan: 🦧"
    },
    {
      "name": ":orthodox_cross:",
      "description": ":orthodox_cross: ☦"
    },
    {
      "name": ":otter:",
      "description": ":otter: 🦦"
    },
    {
      "name": ":outbox_tray:",
      "description": ":outbox_tray: 📤"
    },
    {
      "name": ":owl:",
      "description": ":owl: 🦉"
    },
    {
      "name": ":ox:",
      "description": ":ox: 🐂"
    },
    {
      "name": ":oyster:",
      "description": ":oyster: 🦪"
    },
    {
      "name": ":package:",
      "description": ":package: 📦"
    },
    {
      "name": ":page_facing_up:",
      "description": ":page_facing_up: 📄"
    },
    {
      "name": ":page_with_curl:",
      "description": ":page_with_curl: 📃"
    },
    {
      "name": ":pager:",
      "description": ":pager: 📟"
    },
    {
      "name": ":paintbrush:",
      "description": ":paintbrush: 🖌"
    },
    {
      "name": ":pakistan:",
      "description": ":pakistan: 🇵🇰"
    },
    {
      "name": ":palau:",
      "description": ":palau: 🇵🇼"
    },
    {
      "name": ":palestinian_territories:",
      "description": ":palestinian_territories: 🇵🇸"
    },
    {
      "name": ":palm_tree:",
      "description": ":palm_tree: 🌴"
    },
    {
      "name": ":palms_up_together:",
      "description": ":palms_up_together: 🤲"
    },
    {
      "name": ":panama:",
      "description": ":panama: 🇵🇦"
    },
    {
      "name": ":pancakes:",
      "description": ":pancakes: 🥞"
    },
    {
      "name": ":panda_face:",
      "description": ":panda_face: 🐼"
    },
    {
      "name": ":paperclip:",
      "description": ":paperclip: 📎"
    },
    {
      "name": ":paperclips:",
      "description": ":paperclips: 🖇"
    },
    {
      "name": ":papua_new_guinea:",
      "description": ":papua_new_guinea: 🇵🇬"
    },
    {
      "name": ":parachute:",
      "description": ":parachute: 🪂"
    },
    {
      "name": ":paraguay:",
      "description": ":paraguay: 🇵🇾"
    },
    {
      "name": ":parasol_on_ground:",
      "description": ":parasol_on_ground: ⛱"
    },
    {
      "name": ":parking:",
      "description": ":parking: 🅿"
    },
    {
      "name": ":parrot:",
      "description": ":parrot: 🦜"
    },
    {
      "name": ":part_alternation_mark:",
      "description": ":part_alternation_mark: 〽"
    },
    {
      "name": ":partly_sunny:",
      "description": ":partly_sunny: ⛅"
    },
    {
      "name": ":partying_face:",
      "description": ":partying_face: 🥳"
    },
    {
      "name": ":passenger_ship:",
      "description": ":passenger_ship: 🛳"
    },
    {
      "name": ":passport_control:",
      "description": ":passport_control: 🛂"
    },
    {
      "name": ":pause_button:",
      "description": ":pause_button: ⏸"
    },
    {
      "name": ":paw_prints:",
      "description": ":paw_prints: 🐾"
    },
    {
      "name": ":peace_symbol:",
      "description": ":peace_symbol: ☮"
    },
    {
      "name": ":peach:",
      "description": ":peach: 🍑"
    },
    {
      "name": ":peacock:",
      "description": ":peacock: 🦚"
    },
    {
      "name": ":peanuts:",
      "description": ":peanuts: 🥜"
    },
    {
      "name": ":pear:",
      "description": ":pear: 🍐"
    },
    {
      "name": ":pen:",
      "description": ":pen: 🖊"
    },
    {
      "name": ":pencil:",
      "description": ":pencil: 📝"
    },
    {
      "name": ":pencil2:",
      "description": ":pencil2: ✏"
    },
    {
      "name": ":penguin:",
      "description": ":penguin: 🐧"
    },
    {
      "name": ":pensive:",
      "description": ":pensive: 😔"
    },
    {
      "name": ":people_holding_hands:",
      "description": ":people_holding_hands: 🧑🤝🧑"
    },
    {
      "name": ":people_hugging:",
      "description": ":people_hugging: 🫂"
    },
    {
      "name": ":performing_arts:",
      "description": ":performing_arts: 🎭"
    },
    {
      "name": ":persevere:",
      "description": ":persevere: 😣"
    },
    {
      "name": ":person_bald:",
      "description": ":person_bald: 🧑🦲"
    },
    {
      "name": ":person_curly_hair:",
      "description": ":person_curly_hair: 🧑🦱"
    },
    {
      "name": ":person_feeding_baby:",
      "description": ":person_feeding_baby: 🧑🍼"
    },
    {
      "name": ":person_fencing:",
      "description": ":person_fencing: 🤺"
    },
    {
      "name": ":person_in_manual_wheelchair:",
      "description": ":person_in_manual_wheelchair: 🧑🦽"
    },
    {
      "name": ":person_in_motorized_wheelchair:",
      "description": ":person_in_motorized_wheelchair: 🧑🦼"
    },
    {
      "name": ":person_in_tuxedo:",
      "description": ":person_in_tuxedo: 🤵"
    },
    {
      "name": ":person_red_hair:",
      "description": ":person_red_hair: 🧑🦰"
    },
    {
      "name": ":person_white_hair:",
      "description": ":person_white_hair: 🧑🦳"
    },
    {
      "name": ":person_with_probing_cane:",
      "description": ":person_with_probing_cane: 🧑🦯"
    },
    {
      "name": ":person_with_turban:",
      "description": ":person_with_turban: 👳"
    },
    {
      "name": ":person_with_veil:",
      "description": ":person_with_veil: 👰"
    },
    {
      "name": ":peru:",
      "description": ":peru: 🇵🇪"
    },
    {
      "name": ":petri_dish:",
      "description": ":petri_dish: 🧫"
    },
    {
      "name": ":philippines:",
      "description": ":philippines: 🇵🇭"
    },
    {
      "name": ":phone:",
      "description": ":phone: ☎"
    },
    {
      "name": ":pick:",
      "description": ":pick: ⛏"
    },
    {
      "name": ":pickup_truck:",
      "description": ":pickup_truck: 🛻"
    },
    {
      "name": ":pie:",
      "description": ":pie: 🥧"
    },
    {
      "name": ":pig:",
      "description": ":pig: 🐷"
    },
    {
      "name": ":pig2:",
      "description": ":pig2: 🐖"
    },
    {
      "name": ":pig_nose:",
      "description": ":pig_nose: 🐽"
    },
    {
      "name": ":pill:",
      "description": ":pill: 💊"
    },
    {
      "name": ":pilot:",
      "description": ":pilot: 🧑✈"
    },
    {
      "name": ":pinata:",
      "description": ":pinata: 🪅"
    },
    {
      "name": ":pinched_fingers:",
      "description": ":pinched_fingers: 🤌"
    },
    {
      "name": ":pinching_hand:",
      "description": ":pinching_hand: 🤏"
    },
    {
      "name": ":pineapple:",
      "description": ":pineapple: 🍍"
    },
    {
      "name": ":ping_pong:",
      "description": ":ping_pong: 🏓"
    },
    {
      "name": ":pirate_flag:",
      "description": ":pirate_flag: 🏴☠"
    },
    {
      "name": ":pisces:",
      "description": ":pisces: ♓"
    },
    {
      "name": ":pitcairn_islands:",
      "description": ":pitcairn_islands: 🇵🇳"
    },
    {
      "name": ":pizza:",
      "description": ":pizza: 🍕"
    },
    {
      "name": ":placard:",
      "description": ":placard: 🪧"
    },
    {
      "name": ":place_of_worship:",
      "description": ":place_of_worship: 🛐"
    },
    {
      "name": ":plate_with_cutlery:",
      "description": ":plate_with_cutlery: 🍽"
    },
    {
      "name": ":play_or_pause_button:",
      "description": ":play_or_pause_button: ⏯"
    },
    {
      "name": ":pleading_face:",
      "description": ":pleading_face: 🥺"
    },
    {
      "name": ":plunger:",
      "description": ":plunger: 🪠"
    },
    {
      "name": ":point_down:",
      "description": ":point_down: 👇"
    },
    {
      "name": ":point_left:",
      "description": ":point_left: 👈"
    },
    {
      "name": ":point_right:",
      "description": ":point_right: 👉"
    },
    {
      "name": ":point_up:",
      "description": ":point_up: ☝"
    },
    {
      "name": ":point_up_2:",
      "description": ":point_up_2: 👆"
    },
    {
      "name": ":poland:",
      "description": ":poland: 🇵🇱"
    },
    {
      "name": ":polar_bear:",
      "description": ":polar_bear: 🐻❄"
    },
    {
      "name": ":police_car:",
      "description": ":police_car: 🚓"
    },
    {
      "name": ":police_officer:",
      "description": ":police_officer: 👮"
    },
    {
      "name": ":policeman:",
      "description": ":policeman: 👮♂"
    },
    {
      "name": ":policewoman:",
      "description": ":policewoman: 👮♀"
    },
    {
      "name": ":poodle:",
      "description": ":poodle: 🐩"
    },
    {
      "name": ":poop:",
      "description": ":poop: 💩"
    },
    {
      "name": ":popcorn:",
      "description": ":popcorn: 🍿"
    },
    {
      "name": ":portugal:",
      "description": ":portugal: 🇵🇹"
    },
    {
      "name": ":post_office:",
      "description": ":post_office: 🏣"
    },
    {
      "name": ":postal_horn:",
      "description": ":postal_horn: 📯"
    },
    {
      "name": ":postbox:",
      "description": ":postbox: 📮"
    },
    {
      "name": ":potable_water:",
      "description": ":potable_water: 🚰"
    },
    {
      "name": ":potato:",
      "description": ":potato: 🥔"
    },
    {
      "name": ":potted_plant:",
      "description": ":potted_plant: 🪴"
    },
    {
      "name": ":pouch:",
      "description": ":pouch: 👝"
    },
    {
      "name": ":poultry_leg:",
      "description": ":poultry_leg: 🍗"
    },
    {
      "name": ":pound:",
      "description": ":pound: 💷"
    },
    {
      "name": ":pout:",
      "description": ":pout: 😡"
    },
    {
      "name": ":pouting_cat:",
      "description": ":pouting_cat: 😾"
    },
    {
      "name": ":pouting_face:",
      "description": ":pouting_face: 🙎"
    },
    {
      "name": ":pouting_man:",
      "description": ":pouting_man: 🙎♂"
    },
    {
      "name": ":pouting_woman:",
      "description": ":pouting_woman: 🙎♀"
    },
    {
      "name": ":pray:",
      "description": ":pray: 🙏"
    },
    {
      "name": ":prayer_beads:",
      "description": ":prayer_beads: 📿"
    },
    {
      "name": ":pregnant_woman:",
      "description": ":pregnant_woman: 🤰"
    },
    {
      "name": ":pretzel:",
      "description": ":pretzel: 🥨"
    },
    {
      "name": ":previous_track_button:",
      "description": ":previous_track_button: ⏮"
    },
    {
      "name": ":prince:",
      "description": ":prince: 🤴"
    },
    {
      "name": ":princess:",
      "description": ":princess: 👸"
    },
    {
      "name": ":printer:",
      "description": ":printer: 🖨"
    },
    {
      "name": ":probing_cane:",
      "description": ":probing_cane: 🦯"
    },
    {
      "name": ":puerto_rico:",
      "description": ":puerto_rico: 🇵🇷"
    },
    {
      "name": ":punch:",
      "description": ":punch: 👊"
    },
    {
      "name": ":purple_circle:",
      "description": ":purple_circle: 🟣"
    },
    {
      "name": ":purple_heart:",
      "description": ":purple_heart: 💜"
    },
    {
      "name": ":purple_square:",
      "description": ":purple_square: 🟪"
    },
    {
      "name": ":purse:",
      "description": ":purse: 👛"
    },
    {
      "name": ":pushpin:",
      "description": ":pushpin: 📌"
    },
    {
      "name": ":put_litter_in_its_place:",
      "description": ":put_litter_in_its_place: 🚮"
    },
    {
      "name": ":qatar:",
      "description": ":qatar: 🇶🇦"
    },
    {
      "name": ":question:",
      "description": ":question: ❓"
    },
    {
      "name": ":rabbit:",
      "description": ":rabbit: 🐰"
    },
    {
      "name": ":rabbit2:",
      "description": ":rabbit2: 🐇"
    },
    {
      "name": ":raccoon:",
      "description": ":raccoon: 🦝"
    },
    {
      "name": ":racehorse:",
      "description": ":racehorse: 🐎"
    },
    {
      "name": ":racing_car:",
      "description": ":racing_car: 🏎"
    },
    {
      "name": ":radio:",
      "description": ":radio: 📻"
    },
    {
      "name": ":radio_button:",
      "description": ":radio_button: 🔘"
    },
    {
      "name": ":radioactive:",
      "description": ":radioactive: ☢"
    },
    {
      "name": ":rage:",
      "description": ":rage: 😡"
    },
    {
      "name": ":railway_car:",
      "description": ":railway_car: 🚃"
    },
    {
      "name": ":railway_track:",
      "description": ":railway_track: 🛤"
    },
    {
      "name": ":rainbow:",
      "description": ":rainbow: 🌈"
    },
    {
      "name": ":rainbow_flag:",
      "description": ":rainbow_flag: 🏳🌈"
    },
    {
      "name": ":raised_back_of_hand:",
      "description": ":raised_back_of_hand: 🤚"
    },
    {
      "name": ":raised_eyebrow:",
      "description": ":raised_eyebrow: 🤨"
    },
    {
      "name": ":raised_hand:",
      "description": ":raised_hand: ✋"
    },
    {
      "name": ":raised_hand_with_fingers_splayed:",
      "description": ":raised_hand_with_fingers_splayed: 🖐"
    },
    {
      "name": ":raised_hands:",
      "description": ":raised_hands: 🙌"
    },
    {
      "name": ":raising_hand:",
      "description": ":raising_hand: 🙋"
    },
    {
      "name": ":raising_hand_man:",
      "description": ":raising_hand_man: 🙋♂"
    },
    {
      "name": ":raising_hand_woman:",
      "description": ":raising_hand_woman: 🙋♀"
    },
    {
      "name": ":ram:",
      "description": ":ram: 🐏"
    },
    {
      "name": ":ramen:",
      "description": ":ramen: 🍜"
    },
    {
      "name": ":rat:",
      "description": ":rat: 🐀"
    },
    {
      "name": ":razor:",
      "description": ":razor: 🪒"
    },
    {
      "name": ":receipt:",
      "description": ":receipt: 🧾"
    },
    {
      "name": ":record_button:",
      "description": ":record_button: ⏺"
    },
    {
      "name": ":recycle:",
      "description": ":recycle: ♻"
    },
    {
      "name": ":red_car:",
      "description": ":red_car: 🚗"
    },
    {
      "name": ":red_circle:",
      "description": ":red_circle: 🔴"
    },
    {
      "name": ":red_envelope:",
      "description": ":red_envelope: 🧧"
    },
    {
      "name": ":red_haired_man:",
      "description": ":red_haired_man: 👨🦰"
    },
    {
      "name": ":red_haired_woman:",
      "description": ":red_haired_woman: 👩🦰"
    },
    {
      "name": ":red_square:",
      "description": ":red_square: 🟥"
    },
    {
      "name": ":registered:",
      "description": ":registered: ®"
    },
    {
      "name": ":relaxed:",
      "description": ":relaxed: ☺"
    },
    {
      "name": ":relieved:",
      "description": ":relieved: 😌"
    },
    {
      "name": ":reminder_ribbon:",
      "description": ":reminder_ribbon: 🎗"
    },
    {
      "name": ":repeat:",
      "description": ":repeat: 🔁"
    },
    {
      "name": ":repeat_one:",
      "description": ":repeat_one: 🔂"
    },
    {
      "name": ":rescue_worker_helmet:",
      "description": ":rescue_worker_helmet: ⛑"
    },
    {
      "name": ":restroom:",
      "description": ":restroom: 🚻"
    },
    {
      "name": ":reunion:",
      "description": ":reunion: 🇷🇪"
    },
    {
      "name": ":revolving_hearts:",
      "description": ":revolving_hearts: 💞"
    },
    {
      "name": ":rewind:",
      "description": ":rewind: ⏪"
    },
    {
      "name": ":rhinoceros:",
      "description": ":rhinoceros: 🦏"
    },
    {
      "name": ":ribbon:",
      "description": ":ribbon: 🎀"
    },
    {
      "name": ":rice:",
      "description": ":rice: 🍚"
    },
    {
      "name": ":rice_ball:",
      "description": ":rice_ball: 🍙"
    },
    {
      "name": ":rice_cracker:",
      "description": ":rice_cracker: 🍘"
    },
    {
      "name": ":rice_scene:",
      "description": ":rice_scene: 🎑"
    },
    {
      "name": ":right_anger_bubble:",
      "description": ":right_anger_bubble: 🗯"
    },
    {
      "name": ":ring:",
      "description": ":ring: 💍"
    },
    {
      "name": ":ringed_planet:",
      "description": ":ringed_planet: 🪐"
    },
    {
      "name": ":robot:",
      "description": ":robot: 🤖"
    },
    {
      "name": ":rock:",
      "description": ":rock: 🪨"
    },
    {
      "name": ":rocket:",
      "description": ":rocket: 🚀"
    },
    {
      "name": ":rofl:",
      "description": ":rofl: 🤣"
    },
    {
      "name": ":roll_eyes:",
      "description": ":roll_eyes: 🙄"
    },
    {
      "name": ":roll_of_paper:",
      "description": ":roll_of_paper: 🧻"
    },
    {
      "name": ":roller_coaster:",
      "description": ":roller_coaster: 🎢"
    },
    {
      "name": ":roller_skate:",
      "description": ":roller_skate: 🛼"
    },
    {
      "name": ":romania:",
      "description": ":romania: 🇷🇴"
    },
    {
      "name": ":rooster:",
      "description": ":rooster: 🐓"
    },
    {
      "name": ":rose:",
      "description": ":rose: 🌹"
    },
    {
      "name": ":rosette:",
      "description": ":rosette: 🏵"
    },
    {
      "name": ":rotating_light:",
      "description": ":rotating_light: 🚨"
    },
    {
      "name": ":round_pushpin:",
      "description": ":round_pushpin: 📍"
    },
    {
      "name": ":rowboat:",
      "description": ":rowboat: 🚣"
    },
    {
      "name": ":rowing_man:",
      "description": ":rowing_man: 🚣♂"
    },
    {
      "name": ":rowing_woman:",
      "description": ":rowing_woman: 🚣♀"
    },
    {
      "name": ":ru:",
      "description": ":ru: 🇷🇺"
    },
    {
      "name": ":rugby_football:",
      "description": ":rugby_football: 🏉"
    },
    {
      "name": ":runner:",
      "description": ":runner: 🏃"
    },
    {
      "name": ":running:",
      "description": ":running: 🏃"
    },
    {
      "name": ":running_man:",
      "description": ":running_man: 🏃♂"
    },
    {
      "name": ":running_shirt_with_sash:",
      "description": ":running_shirt_with_sash: 🎽"
    },
    {
      "name": ":running_woman:",
      "description": ":running_woman: 🏃♀"
    },
    {
      "name": ":rwanda:",
      "description": ":rwanda: 🇷🇼"
    },
    {
      "name": ":sa:",
      "description": ":sa: 🈂"
    },
    {
      "name": ":safety_pin:",
      "description": ":safety_pin: 🧷"
    },
    {
      "name": ":safety_vest:",
      "description": ":safety_vest: 🦺"
    },
    {
      "name": ":sagittarius:",
      "description": ":sagittarius: ♐"
    },
    {
      "name": ":sailboat:",
      "description": ":sailboat: ⛵"
    },
    {
      "name": ":sake:",
      "description": ":sake: 🍶"
    },
    {
      "name": ":salt:",
      "description": ":salt: 🧂"
    },
    {
      "name": ":samoa:",
      "description": ":samoa: 🇼🇸"
    },
    {
      "name": ":san_marino:",
      "description": ":san_marino: 🇸🇲"
    },
    {
      "name": ":sandal:",
      "description": ":sandal: 👡"
    },
    {
      "name": ":sandwich:",
      "description": ":sandwich: 🥪"
    },
    {
      "name": ":santa:",
      "description": ":santa: 🎅"
    },
    {
      "name": ":sao_tome_principe:",
      "description": ":sao_tome_principe: 🇸🇹"
    },
    {
      "name": ":sari:",
      "description": ":sari: 🥻"
    },
    {
      "name": ":sassy_man:",
      "description": ":sassy_man: 💁♂"
    },
    {
      "name": ":sassy_woman:",
      "description": ":sassy_woman: 💁♀"
    },
    {
      "name": ":satellite:",
      "description": ":satellite: 📡"
    },
    {
      "name": ":satisfied:",
      "description": ":satisfied: 😆"
    },
    {
      "name": ":saudi_arabia:",
      "description": ":saudi_arabia: 🇸🇦"
    },
    {
      "name": ":sauna_man:",
      "description": ":sauna_man: 🧖♂"
    },
    {
      "name": ":sauna_person:",
      "description": ":sauna_person: 🧖"
    },
    {
      "name": ":sauna_woman:",
      "description": ":sauna_woman: 🧖♀"
    },
    {
      "name": ":sauropod:",
      "description": ":sauropod: 🦕"
    },
    {
      "name": ":saxophone:",
      "description": ":saxophone: 🎷"
    },
    {
      "name": ":scarf:",
      "description": ":scarf: 🧣"
    },
    {
      "name": ":school:",
      "description": ":school: 🏫"
    },
    {
      "name": ":school_satchel:",
      "description": ":school_satchel: 🎒"
    },
    {
      "name": ":scientist:",
      "description": ":scientist: 🧑🔬"
    },
    {
      "name": ":scissors:",
      "description": ":scissors: ✂"
    },
    {
      "name": ":scorpion:",
      "description": ":scorpion: 🦂"
    },
    {
      "name": ":scorpius:",
      "description": ":scorpius: ♏"
    },
    {
      "name": ":scotland:",
      "description": ":scotland: 🏴󠁧󠁢󠁳󠁣󠁴󠁿"
    },
    {
      "name": ":scream:",
      "description": ":scream: 😱"
    },
    {
      "name": ":scream_cat:",
      "description": ":scream_cat: 🙀"
    },
    {
      "name": ":screwdriver:",
      "description": ":screwdriver: 🪛"
    },
    {
      "name": ":scroll:",
      "description": ":scroll: 📜"
    },
    {
      "name": ":seal:",
      "description": ":seal: 🦭"
    },
    {
      "name": ":seat:",
      "description": ":seat: 💺"
    },
    {
      "name": ":secret:",
      "description": ":secret: ㊙"
    },
    {
      "name": ":see_no_evil:",
      "description": ":see_no_evil: 🙈"
    },
    {
      "name": ":seedling:",
      "description": ":seedling: 🌱"
    },
    {
      "name": ":selfie:",
      "description": ":selfie: 🤳"
    },
    {
      "name": ":senegal:",
      "description": ":senegal: 🇸🇳"
    },
    {
      "name": ":serbia:",
      "description": ":serbia: 🇷🇸"
    },
    {
      "name": ":service_dog:",
      "description": ":service_dog: 🐕🦺"
    },
    {
      "name": ":seven:",
      "description": ":seven: 7⃣"
    },
    {
      "name": ":sewing_needle:",
      "description": ":sewing_needle: 🪡"
    },
    {
      "name": ":seychelles:",
      "description": ":seychelles: 🇸🇨"
    },
    {
      "name": ":shallow_pan_of_food:",
      "description": ":shallow_pan_of_food: 🥘"
    },
    {
      "name": ":shamrock:",
      "description": ":shamrock: ☘"
    },
    {
      "name": ":shark:",
      "description": ":shark: 🦈"
    },
    {
      "name": ":shaved_ice:",
      "description": ":shaved_ice: 🍧"
    },
    {
      "name": ":sheep:",
      "description": ":sheep: 🐑"
    },
    {
      "name": ":shell:",
      "description": ":shell: 🐚"
    },
    {
      "name": ":shield:",
      "description": ":shield: 🛡"
    },
    {
      "name": ":shinto_shrine:",
      "description": ":shinto_shrine: ⛩"
    },
    {
      "name": ":ship:",
      "description": ":ship: 🚢"
    },
    {
      "name": ":shirt:",
      "description": ":shirt: 👕"
    },
    {
      "name": ":shit:",
      "description": ":shit: 💩"
    },
    {
      "name": ":shoe:",
      "description": ":shoe: 👞"
    },
    {
      "name": ":shopping:",
      "description": ":shopping: 🛍"
    },
    {
      "name": ":shopping_cart:",
      "description": ":shopping_cart: 🛒"
    },
    {
      "name": ":shorts:",
      "description": ":shorts: 🩳"
    },
    {
      "name": ":shower:",
      "description": ":shower: 🚿"
    },
    {
      "name": ":shrimp:",
      "description": ":shrimp: 🦐"
    },
    {
      "name": ":shrug:",
      "description": ":shrug: 🤷"
    },
    {
      "name": ":shushing_face:",
      "description": ":shushing_face: 🤫"
    },
    {
      "name": ":sierra_leone:",
      "description": ":sierra_leone: 🇸🇱"
    },
    {
      "name": ":signal_strength:",
      "description": ":signal_strength: 📶"
    },
    {
      "name": ":singapore:",
      "description": ":singapore: 🇸🇬"
    },
    {
      "name": ":singer:",
      "description": ":singer: 🧑🎤"
    },
    {
      "name": ":sint_maarten:",
      "description": ":sint_maarten: 🇸🇽"
    },
    {
      "name": ":six:",
      "description": ":six: 6⃣"
    },
    {
      "name": ":six_pointed_star:",
      "description": ":six_pointed_star: 🔯"
    },
    {
      "name": ":skateboard:",
      "description": ":skateboard: 🛹"
    },
    {
      "name": ":ski:",
      "description": ":ski: 🎿"
    },
    {
      "name": ":skier:",
      "description": ":skier: ⛷"
    },
    {
      "name": ":skull:",
      "description": ":skull: 💀"
    },
    {
      "name": ":skull_and_crossbones:",
      "description": ":skull_and_crossbones: ☠"
    },
    {
      "name": ":skunk:",
      "description": ":skunk: 🦨"
    },
    {
      "name": ":sled:",
      "description": ":sled: 🛷"
    },
    {
      "name": ":sleeping:",
      "description": ":sleeping: 😴"
    },
    {
      "name": ":sleeping_bed:",
      "description": ":sleeping_bed: 🛌"
    },
    {
      "name": ":sleepy:",
      "description": ":sleepy: 😪"
    },
    {
      "name": ":slightly_frowning_face:",
      "description": ":slightly_frowning_face: 🙁"
    },
    {
      "name": ":slightly_smiling_face:",
      "description": ":slightly_smiling_face: 🙂"
    },
    {
      "name": ":slot_machine:",
      "description": ":slot_machine: 🎰"
    },
    {
      "name": ":sloth:",
      "description": ":sloth: 🦥"
    },
    {
      "name": ":slovakia:",
      "description": ":slovakia: 🇸🇰"
    },
    {
      "name": ":slovenia:",
      "description": ":slovenia: 🇸🇮"
    },
    {
      "name": ":small_airplane:",
      "description": ":small_airplane: 🛩"
    },
    {
      "name": ":small_blue_diamond:",
      "description": ":small_blue_diamond: 🔹"
    },
    {
      "name": ":small_orange_diamond:",
      "description": ":small_orange_diamond: 🔸"
    },
    {
      "name": ":small_red_triangle:",
      "description": ":small_red_triangle: 🔺"
    },
    {
      "name": ":small_red_triangle_down:",
      "description": ":small_red_triangle_down: 🔻"
    },
    {
      "name": ":smile:",
      "description": ":smile: 😄"
    },
    {
      "name": ":smile_cat:",
      "description": ":smile_cat: 😸"
    },
    {
      "name": ":smiley:",
      "description": ":smiley: 😃"
    },
    {
      "name": ":smiley_cat:",
      "description": ":smiley_cat: 😺"
    },
    {
      "name": ":smiling_face_with_tear:",
      "description": ":smiling_face_with_tear: 🥲"
    },
    {
      "name": ":smiling_face_with_three_hearts:",
      "description": ":smiling_face_with_three_hearts: 🥰"
    },
    {
      "name": ":smiling_imp:",
      "description": ":smiling_imp: 😈"
    },
    {
      "name": ":smirk:",
      "description": ":smirk: 😏"
    },
    {
      "name": ":smirk_cat:",
      "description": ":smirk_cat: 😼"
    },
    {
      "name": ":smoking:",
      "description": ":smoking: 🚬"
    },
    {
      "name": ":snail:",
      "description": ":snail: 🐌"
    },
    {
      "name": ":snake:",
      "description": ":snake: 🐍"
    },
    {
      "name": ":sneezing_face:",
      "description": ":sneezing_face: 🤧"
    },
    {
      "name": ":snowboarder:",
      "description": ":snowboarder: 🏂"
    },
    {
      "name": ":snowflake:",
      "description": ":snowflake: ❄"
    },
    {
      "name": ":snowman:",
      "description": ":snowman: ⛄"
    },
    {
      "name": ":snowman_with_snow:",
      "description": ":snowman_with_snow: ☃"
    },
    {
      "name": ":soap:",
      "description": ":soap: 🧼"
    },
    {
      "name": ":sob:",
      "description": ":sob: 😭"
    },
    {
      "name": ":soccer:",
      "description": ":soccer: ⚽"
    },
    {
      "name": ":socks:",
      "description": ":socks: 🧦"
    },
    {
      "name": ":softball:",
      "description": ":softball: 🥎"
    },
    {
      "name": ":solomon_islands:",
      "description": ":solomon_islands: 🇸🇧"
    },
    {
      "name": ":somalia:",
      "description": ":somalia: 🇸🇴"
    },
    {
      "name": ":soon:",
      "description": ":soon: 🔜"
    },
    {
      "name": ":sos:",
      "description": ":sos: 🆘"
    },
    {
      "name": ":sound:",
      "description": ":sound: 🔉"
    },
    {
      "name": ":south_africa:",
      "description": ":south_africa: 🇿🇦"
    },
    {
      "name": ":south_georgia_south_sandwich_islands:",
      "description": ":south_georgia_south_sandwich_islands: 🇬🇸"
    },
    {
      "name": ":south_sudan:",
      "description": ":south_sudan: 🇸🇸"
    },
    {
      "name": ":space_invader:",
      "description": ":space_invader: 👾"
    },
    {
      "name": ":spades:",
      "description": ":spades: ♠"
    },
    {
      "name": ":spaghetti:",
      "description": ":spaghetti: 🍝"
    },
    {
      "name": ":sparkle:",
      "description": ":sparkle: ❇"
    },
    {
      "name": ":sparkler:",
      "description": ":sparkler: 🎇"
    },
    {
      "name": ":sparkles:",
      "description": ":sparkles: ✨"
    },
    {
      "name": ":sparkling_heart:",
      "description": ":sparkling_heart: 💖"
    },
    {
      "name": ":speak_no_evil:",
      "description": ":speak_no_evil: 🙊"
    },
    {
      "name": ":speaker:",
      "description": ":speaker: 🔈"
    },
    {
      "name": ":speaking_head:",
      "description": ":speaking_head: 🗣"
    },
    {
      "name": ":speech_balloon:",
      "description": ":speech_balloon: 💬"
    },
    {
      "name": ":speedboat:",
      "description": ":speedboat: 🚤"
    },
    {
      "name": ":spider:",
      "description": ":spider: 🕷"
    },
    {
      "name": ":spider_web:",
      "description": ":spider_web: 🕸"
    },
    {
      "name": ":spiral_calendar:",
      "description": ":spiral_calendar: 🗓"
    },
    {
      "name": ":spiral_notepad:",
      "description": ":spiral_notepad: 🗒"
    },
    {
      "name": ":sponge:",
      "description": ":sponge: 🧽"
    },
    {
      "name": ":spoon:",
      "description": ":spoon: 🥄"
    },
    {
      "name": ":squid:",
      "description": ":squid: 🦑"
    },
    {
      "name": ":sri_lanka:",
      "description": ":sri_lanka: 🇱🇰"
    },
    {
      "name": ":st_barthelemy:",
      "description": ":st_barthelemy: 🇧🇱"
    },
    {
      "name": ":st_helena:",
      "description": ":st_helena: 🇸🇭"
    },
    {
      "name": ":st_kitts_nevis:",
      "description": ":st_kitts_nevis: 🇰🇳"
    },
    {
      "name": ":st_lucia:",
      "description": ":st_lucia: 🇱🇨"
    },
    {
      "name": ":st_martin:",
      "description": ":st_martin: 🇲🇫"
    },
    {
      "name": ":st_pierre_miquelon:",
      "description": ":st_pierre_miquelon: 🇵🇲"
    },
    {
      "name": ":st_vincent_grenadines:",
      "description": ":st_vincent_grenadines: 🇻🇨"
    },
    {
      "name": ":stadium:",
      "description": ":stadium: 🏟"
    },
    {
      "name": ":standing_man:",
      "description": ":standing_man: 🧍♂"
    },
    {
      "name": ":standing_person:",
      "description": ":standing_person: 🧍"
    },
    {
      "name": ":standing_woman:",
      "description": ":standing_woman: 🧍♀"
    },
    {
      "name": ":star:",
      "description": ":star: ⭐"
    },
    {
      "name": ":star2:",
      "description": ":star2: 🌟"
    },
    {
      "name": ":star_and_crescent:",
      "description": ":star_and_crescent: ☪"
    },
    {
      "name": ":star_of_david:",
      "description": ":star_of_david: ✡"
    },
    {
      "name": ":star_struck:",
      "description": ":star_struck: 🤩"
    },
    {
      "name": ":stars:",
      "description": ":stars: 🌠"
    },
    {
      "name": ":station:",
      "description": ":station: 🚉"
    },
    {
      "name": ":statue_of_liberty:",
      "description": ":statue_of_liberty: 🗽"
    },
    {
      "name": ":steam_locomotive:",
      "description": ":steam_locomotive: 🚂"
    },
    {
      "name": ":stethoscope:",
      "description": ":stethoscope: 🩺"
    },
    {
      "name": ":stew:",
      "description": ":stew: 🍲"
    },
    {
      "name": ":stop_button:",
      "description": ":stop_button: ⏹"
    },
    {
      "name": ":stop_sign:",
      "description": ":stop_sign: 🛑"
    },
    {
      "name": ":stopwatch:",
      "description": ":stopwatch: ⏱"
    },
    {
      "name": ":straight_ruler:",
      "description": ":straight_ruler: 📏"
    },
    {
      "name": ":strawberry:",
      "description": ":strawberry: 🍓"
    },
    {
      "name": ":stuck_out_tongue:",
      "description": ":stuck_out_tongue: 😛"
    },
    {
      "name": ":stuck_out_tongue_closed_eyes:",
      "description": ":stuck_out_tongue_closed_eyes: 😝"
    },
    {
      "name": ":stuck_out_tongue_winking_eye:",
      "description": ":stuck_out_tongue_winking_eye: 😜"
    },
    {
      "name": ":student:",
      "description": ":student: 🧑🎓"
    },
    {
      "name": ":studio_microphone:",
      "description": ":studio_microphone: 🎙"
    },
    {
      "name": ":stuffed_flatbread:",
      "description": ":stuffed_flatbread: 🥙"
    },
    {
      "name": ":sudan:",
      "description": ":sudan: 🇸🇩"
    },
    {
      "name": ":sun_behind_large_cloud:",
      "description": ":sun_behind_large_cloud: 🌥"
    },
    {
      "name": ":sun_behind_rain_cloud:",
      "description": ":sun_behind_rain_cloud: 🌦"
    },
    {
      "name": ":sun_behind_small_cloud:",
      "description": ":sun_behind_small_cloud: 🌤"
    },
    {
      "name": ":sun_with_face:",
      "description": ":sun_with_face: 🌞"
    },
    {
      "name": ":sunflower:",
      "description": ":sunflower: 🌻"
    },
    {
      "name": ":sunglasses:",
      "description": ":sunglasses: 😎"
    },
    {
      "name": ":sunny:",
      "description": ":sunny: ☀"
    },
    {
      "name": ":sunrise:",
      "description": ":sunrise: 🌅"
    },
    {
      "name": ":sunrise_over_mountains:",
      "description": ":sunrise_over_mountains: 🌄"
    },
    {
      "name": ":superhero:",
      "description": ":superhero: 🦸"
    },
    {
      "name": ":superhero_man:",
      "description": ":superhero_man: 🦸♂"
    },
    {
      "name": ":superhero_woman:",
      "description": ":superhero_woman: 🦸♀"
    },
    {
      "name": ":supervillain:",
      "description": ":supervillain: 🦹"
    },
    {
      "name": ":supervillain_man:",
      "description": ":supervillain_man: 🦹♂"
    },
    {
      "name": ":supervillain_woman:",
      "description": ":supervillain_woman: 🦹♀"
    },
    {
      "name": ":surfer:",
      "description": ":surfer: 🏄"
    },
    {
      "name": ":surfing_man:",
      "description": ":surfing_man: 🏄♂"
    },
    {
      "name": ":surfing_woman:",
      "description": ":surfing_woman: 🏄♀"
    },
    {
      "name": ":suriname:",
      "description": ":suriname: 🇸🇷"
    },
    {
      "name": ":sushi:",
      "description": ":sushi: 🍣"
    },
    {
      "name": ":suspension_railway:",
      "description": ":suspension_railway: 🚟"
    },
    {
      "name": ":svalbard_jan_mayen:",
      "description": ":svalbard_jan_mayen: 🇸🇯"
    },
    {
      "name": ":swan:",
      "description": ":swan: 🦢"
    },
    {
      "name": ":swaziland:",
      "description": ":swaziland: 🇸🇿"
    },
    {
      "name": ":sweat:",
      "description": ":sweat: 😓"
    },
    {
      "name": ":sweat_drops:",
      "description": ":sweat_drops: 💦"
    },
    {
      "name": ":sweat_smile:",
      "description": ":sweat_smile: 😅"
    },
    {
      "name": ":sweden:",
      "description": ":sweden: 🇸🇪"
    },
    {
      "name": ":sweet_potato:",
      "description": ":sweet_potato: 🍠"
    },
    {
      "name": ":swim_brief:",
      "description": ":swim_brief: 🩲"
    },
    {
      "name": ":swimmer:",
      "description": ":swimmer: 🏊"
    },
    {
      "name": ":swimming_man:",
      "description": ":swimming_man: 🏊♂"
    },
    {
      "name": ":swimming_woman:",
      "description": ":swimming_woman: 🏊♀"
    },
    {
      "name": ":switzerland:",
      "description": ":switzerland: 🇨🇭"
    },
    {
      "name": ":symbols:",
      "description": ":symbols: 🔣"
    },
    {
      "name": ":synagogue:",
      "description": ":synagogue: 🕍"
    },
    {
      "name": ":syria:",
      "description": ":syria: 🇸🇾"
    },
    {
      "name": ":syringe:",
      "description": ":syringe: 💉"
    },
    {
      "name": ":t-rex:",
      "description": ":t-rex: 🦖"
    },
    {
      "name": ":taco:",
      "description": ":taco: 🌮"
    },
    {
      "name": ":tada:",
      "description": ":tada: 🎉"
    },
    {
      "name": ":taiwan:",
      "description": ":taiwan: 🇹🇼"
    },
    {
      "name": ":tajikistan:",
      "description": ":tajikistan: 🇹🇯"
    },
    {
      "name": ":takeout_box:",
      "description": ":takeout_box: 🥡"
    },
    {
      "name": ":tamale:",
      "description": ":tamale: 🫔"
    },
    {
      "name": ":tanabata_tree:",
      "description": ":tanabata_tree: 🎋"
    },
    {
      "name": ":tangerine:",
      "description": ":tangerine: 🍊"
    },
    {
      "name": ":tanzania:",
      "description": ":tanzania: 🇹🇿"
    },
    {
      "name": ":taurus:",
      "description": ":taurus: ♉"
    },
    {
      "name": ":taxi:",
      "description": ":taxi: 🚕"
    },
    {
      "name": ":tea:",
      "description": ":tea: 🍵"
    },
    {
      "name": ":teacher:",
      "description": ":teacher: 🧑🏫"
    },
    {
      "name": ":teapot:",
      "description": ":teapot: 🫖"
    },
    {
      "name": ":technologist:",
      "description": ":technologist: 🧑💻"
    },
    {
      "name": ":teddy_bear:",
      "description": ":teddy_bear: 🧸"
    },
    {
      "name": ":telephone:",
      "description": ":telephone: ☎"
    },
    {
      "name": ":telephone_receiver:",
      "description": ":telephone_receiver: 📞"
    },
    {
      "name": ":telescope:",
      "description": ":telescope: 🔭"
    },
    {
      "name": ":tennis:",
      "description": ":tennis: 🎾"
    },
    {
      "name": ":tent:",
      "description": ":tent: ⛺"
    },
    {
      "name": ":test_tube:",
      "description": ":test_tube: 🧪"
    },
    {
      "name": ":thailand:",
      "description": ":thailand: 🇹🇭"
    },
    {
      "name": ":thermometer:",
      "description": ":thermometer: 🌡"
    },
    {
      "name": ":thinking:",
      "description": ":thinking: 🤔"
    },
    {
      "name": ":thong_sandal:",
      "description": ":thong_sandal: 🩴"
    },
    {
      "name": ":thought_balloon:",
      "description": ":thought_balloon: 💭"
    },
    {
      "name": ":thread:",
      "description": ":thread: 🧵"
    },
    {
      "name": ":three:",
      "description": ":three: 3⃣"
    },
    {
      "name": ":thumbsdown:",
      "description": ":thumbsdown: 👎"
    },
    {
      "name": ":thumbsup:",
      "description": ":thumbsup: 👍"
    },
    {
      "name": ":ticket:",
      "description": ":ticket: 🎫"
    },
    {
      "name": ":tickets:",
      "description": ":tickets: 🎟"
    },
    {
      "name": ":tiger:",
      "description": ":tiger: 🐯"
    },
    {
      "name": ":tiger2:",
      "description": ":tiger2: 🐅"
    },
    {
      "name": ":timer_clock:",
      "description": ":timer_clock: ⏲"
    },
    {
      "name": ":timor_leste:",
      "description": ":timor_leste: 🇹🇱"
    },
    {
      "name": ":tipping_hand_man:",
      "description": ":tipping_hand_man: 💁♂"
    },
    {
      "name": ":tipping_hand_person:",
      "description": ":tipping_hand_person: 💁"
    },
    {
      "name": ":tipping_hand_woman:",
      "description": ":tipping_hand_woman: 💁♀"
    },
    {
      "name": ":tired_face:",
      "description": ":tired_face: 😫"
    },
    {
      "name": ":tm:",
      "description": ":tm: ™"
    },
    {
      "name": ":togo:",
      "description": ":togo: 🇹🇬"
    },
    {
      "name": ":toilet:",
      "description": ":toilet: 🚽"
    },
    {
      "name": ":tokelau:",
      "description": ":tokelau: 🇹🇰"
    },
    {
      "name": ":tokyo_tower:",
      "description": ":tokyo_tower: 🗼"
    },
    {
      "name": ":tomato:",
      "description": ":tomato: 🍅"
    },
    {
      "name": ":tonga:",
      "description": ":tonga: 🇹🇴"
    },
    {
      "name": ":tongue:",
      "description": ":tongue: 👅"
    },
    {
      "name": ":toolbox:",
      "description": ":toolbox: 🧰"
    },
    {
      "name": ":tooth:",
      "description": ":tooth: 🦷"
    },
    {
      "name": ":toothbrush:",
      "description": ":toothbrush: 🪥"
    },
    {
      "name": ":top:",
      "description": ":top: 🔝"
    },
    {
      "name": ":tophat:",
      "description": ":tophat: 🎩"
    },
    {
      "name": ":tornado:",
      "description": ":tornado: 🌪"
    },
    {
      "name": ":tr:",
      "description": ":tr: 🇹🇷"
    },
    {
      "name": ":trackball:",
      "description": ":trackball: 🖲"
    },
    {
      "name": ":tractor:",
      "description": ":tractor: 🚜"
    },
    {
      "name": ":traffic_light:",
      "description": ":traffic_light: 🚥"
    },
    {
      "name": ":train:",
      "description": ":train: 🚋"
    },
    {
      "name": ":train2:",
      "description": ":train2: 🚆"
    },
    {
      "name": ":tram:",
      "description": ":tram: 🚊"
    },
    {
      "name": ":transgender_flag:",
      "description": ":transgender_flag: 🏳⚧"
    },
    {
      "name": ":transgender_symbol:",
      "description": ":transgender_symbol: ⚧"
    },
    {
      "name": ":triangular_flag_on_post:",
      "description": ":triangular_flag_on_post: 🚩"
    },
    {
      "name": ":triangular_ruler:",
      "description": ":triangular_ruler: 📐"
    },
    {
      "name": ":trident:",
      "description": ":trident: 🔱"
    },
    {
      "name": ":trinidad_tobago:",
      "description": ":trinidad_tobago: 🇹🇹"
    },
    {
      "name": ":tristan_da_cunha:",
      "description": ":tristan_da_cunha: 🇹🇦"
    },
    {
      "name": ":triumph:",
      "description": ":triumph: 😤"
    },
    {
      "name": ":trolleybus:",
      "description": ":trolleybus: 🚎"
    },
    {
      "name": ":trophy:",
      "description": ":trophy: 🏆"
    },
    {
      "name": ":tropical_drink:",
      "description": ":tropical_drink: 🍹"
    },
    {
      "name": ":tropical_fish:",
      "description": ":tropical_fish: 🐠"
    },
    {
      "name": ":truck:",
      "description": ":truck: 🚚"
    },
    {
      "name": ":trumpet:",
      "description": ":trumpet: 🎺"
    },
    {
      "name": ":tshirt:",
      "description": ":tshirt: 👕"
    },
    {
      "name": ":tulip:",
      "description": ":tulip: 🌷"
    },
    {
      "name": ":tumbler_glass:",
      "description": ":tumbler_glass: 🥃"
    },
    {
      "name": ":tunisia:",
      "description": ":tunisia: 🇹🇳"
    },
    {
      "name": ":turkey:",
      "description": ":turkey: 🦃"
    },
    {
      "name": ":turkmenistan:",
      "description": ":turkmenistan: 🇹🇲"
    },
    {
      "name": ":turks_caicos_islands:",
      "description": ":turks_caicos_islands: 🇹🇨"
    },
    {
      "name": ":turtle:",
      "description": ":turtle: 🐢"
    },
    {
      "name": ":tuvalu:",
      "description": ":tuvalu: 🇹🇻"
    },
    {
      "name": ":tv:",
      "description": ":tv: 📺"
    },
    {
      "name": ":twisted_rightwards_arrows:",
      "description": ":twisted_rightwards_arrows: 🔀"
    },
    {
      "name": ":two:",
      "description": ":two: 2⃣"
    },
    {
      "name": ":two_hearts:",
      "description": ":two_hearts: 💕"
    },
    {
      "name": ":two_men_holding_hands:",
      "description": ":two_men_holding_hands: 👬"
    },
    {
      "name": ":two_women_holding_hands:",
      "description": ":two_women_holding_hands: 👭"
    },
    {
      "name": ":u5272:",
      "description": ":u5272: 🈹"
    },
    {
      "name": ":u5408:",
      "description": ":u5408: 🈴"
    },
    {
      "name": ":u55b6:",
      "description": ":u55b6: 🈺"
    },
    {
      "name": ":u6307:",
      "description": ":u6307: 🈯"
    },
    {
      "name": ":u6708:",
      "description": ":u6708: 🈷"
    },
    {
      "name": ":u6709:",
      "description": ":u6709: 🈶"
    },
    {
      "name": ":u6e80:",
      "description": ":u6e80: 🈵"
    },
    {
      "name": ":u7121:",
      "description": ":u7121: 🈚"
    },
    {
      "name": ":u7533:",
      "description": ":u7533: 🈸"
    },
    {
      "name": ":u7981:",
      "description": ":u7981: 🈲"
    },
    {
      "name": ":u7a7a:",
      "description": ":u7a7a: 🈳"
    },
    {
      "name": ":uganda:",
      "description": ":uganda: 🇺🇬"
    },
    {
      "name": ":uk:",
      "description": ":uk: 🇬🇧"
    },
    {
      "name": ":ukraine:",
      "description": ":ukraine: 🇺🇦"
    },
    {
      "name": ":umbrella:",
      "description": ":umbrella: ☔"
    },
    {
      "name": ":unamused:",
      "description": ":unamused: 😒"
    },
    {
      "name": ":underage:",
      "description": ":underage: 🔞"
    },
    {
      "name": ":unicorn:",
      "description": ":unicorn: 🦄"
    },
    {
      "name": ":united_arab_emirates:",
      "description": ":united_arab_emirates: 🇦🇪"
    },
    {
      "name": ":united_nations:",
      "description": ":united_nations: 🇺🇳"
    },
    {
      "name": ":unlock:",
      "description": ":unlock: 🔓"
    },
    {
      "name": ":up:",
      "description": ":up: 🆙"
    },
    {
      "name": ":upside_down_face:",
      "description": ":upside_down_face: 🙃"
    },
    {
      "name": ":uruguay:",
      "description": ":uruguay: 🇺🇾"
    },
    {
      "name": ":us:",
      "description": ":us: 🇺🇸"
    },
    {
      "name": ":us_outlying_islands:",
      "description": ":us_outlying_islands: 🇺🇲"
    },
    {
      "name": ":us_virgin_islands:",
      "description": ":us_virgin_islands: 🇻🇮"
    },
    {
      "name": ":uzbekistan:",
      "description": ":uzbekistan: 🇺🇿"
    },
    {
      "name": ":v:",
      "description": ":v: ✌"
    },
    {
      "name": ":vampire:",
      "description": ":vampire: 🧛"
    },
    {
      "name": ":vampire_man:",
      "description": ":vampire_man: 🧛♂"
    },
    {
      "name": ":vampire_woman:",
      "description": ":vampire_woman: 🧛♀"
    },
    {
      "name": ":vanuatu:",
      "description": ":vanuatu: 🇻🇺"
    },
    {
      "name": ":vatican_city:",
      "description": ":vatican_city: 🇻🇦"
    },
    {
      "name": ":venezuela:",
      "description": ":venezuela: 🇻🇪"
    },
    {
      "name": ":vertical_traffic_light:",
      "description": ":vertical_traffic_light: 🚦"
    },
    {
      "name": ":vhs:",
      "description": ":vhs: 📼"
    },
    {
      "name": ":vibration_mode:",
      "description": ":vibration_mode: 📳"
    },
    {
      "name": ":video_camera:",
      "description": ":video_camera: 📹"
    },
    {
      "name": ":video_game:",
      "description": ":video_game: 🎮"
    },
    {
      "name": ":vietnam:",
      "description": ":vietnam: 🇻🇳"
    },
    {
      "name": ":violin:",
      "description": ":violin: 🎻"
    },
    {
      "name": ":virgo:",
      "description": ":virgo: ♍"
    },
    {
      "name": ":volcano:",
      "description": ":volcano: 🌋"
    },
    {
      "name": ":volleyball:",
      "description": ":volleyball: 🏐"
    },
    {
      "name": ":vomiting_face:",
      "description": ":vomiting_face: 🤮"
    },
    {
      "name": ":vs:",
      "description": ":vs: 🆚"
    },
    {
      "name": ":vulcan_salute:",
      "description": ":vulcan_salute: 🖖"
    },
    {
      "name": ":waffle:",
      "description": ":waffle: 🧇"
    },
    {
      "name": ":wales:",
      "description": ":wales: 🏴󠁧󠁢󠁷󠁬󠁳󠁿"
    },
    {
      "name": ":walking:",
      "description": ":walking: 🚶"
    },
    {
      "name": ":walking_man:",
      "description": ":walking_man: 🚶♂"
    },
    {
      "name": ":walking_woman:",
      "description": ":walking_woman: 🚶♀"
    },
    {
      "name": ":wallis_futuna:",
      "description": ":wallis_futuna: 🇼🇫"
    },
    {
      "name": ":waning_crescent_moon:",
      "description": ":waning_crescent_moon: 🌘"
    },
    {
      "name": ":waning_gibbous_moon:",
      "description": ":waning_gibbous_moon: 🌖"
    },
    {
      "name": ":warning:",
      "description": ":warning: ⚠"
    },
    {
      "name": ":wastebasket:",
      "description": ":wastebasket: 🗑"
    },
    {
      "name": ":watch:",
      "description": ":watch: ⌚"
    },
    {
      "name": ":water_buffalo:",
      "description": ":water_buffalo: 🐃"
    },
    {
      "name": ":water_polo:",
      "description": ":water_polo: 🤽"
    },
    {
      "name": ":watermelon:",
      "description": ":watermelon: 🍉"
    },
    {
      "name": ":wave:",
      "description": ":wave: 👋"
    },
    {
      "name": ":wavy_dash:",
      "description": ":wavy_dash: 〰"
    },
    {
      "name": ":waxing_crescent_moon:",
      "description": ":waxing_crescent_moon: 🌒"
    },
    {
      "name": ":waxing_gibbous_moon:",
      "description": ":waxing_gibbous_moon: 🌔"
    },
    {
      "name": ":wc:",
      "description": ":wc: 🚾"
    },
    {
      "name": ":weary:",
      "description": ":weary: 😩"
    },
    {
      "name": ":wedding:",
      "description": ":wedding: 💒"
    },
    {
      "name": ":weight_lifting:",
      "description": ":weight_lifting: 🏋"
    },
    {
      "name": ":weight_lifting_man:",
      "description": ":weight_lifting_man: 🏋♂"
    },
    {
      "name": ":weight_lifting_woman:",
      "description": ":weight_lifting_woman: 🏋♀"
    },
    {
      "name": ":western_sahara:",
      "description": ":western_sahara: 🇪🇭"
    },
    {
      "name": ":whale:",
      "description": ":whale: 🐳"
    },
    {
      "name": ":whale2:",
      "description": ":whale2: 🐋"
    },
    {
      "name": ":wheel_of_dharma:",
      "description": ":wheel_of_dharma: ☸"
    },
    {
      "name": ":wheelchair:",
      "description": ":wheelchair: ♿"
    },
    {
      "name": ":white_check_mark:",
      "description": ":white_check_mark: ✅"
    },
    {
      "name": ":white_circle:",
      "description": ":white_circle: ⚪"
    },
    {
      "name": ":white_flag:",
      "description": ":white_flag: 🏳"
    },
    {
      "name": ":white_flower:",
      "description": ":white_flower: 💮"
    },
    {
      "name": ":white_haired_man:",
      "description": ":white_haired_man: 👨🦳"
    },
    {
      "name": ":white_haired_woman:",
      "description": ":white_haired_woman: 👩🦳"
    },
    {
      "name": ":white_heart:",
      "description": ":white_heart: 🤍"
    },
    {
      "name": ":white_large_square:",
      "description": ":white_large_square: ⬜"
    },
    {
      "name": ":white_medium_small_square:",
      "description": ":white_medium_small_square: ◽"
    },
    {
      "name": ":white_medium_square:",
      "description": ":white_medium_square: ◻"
    },
    {
      "name": ":white_small_square:",
      "description": ":white_small_square: ▫"
    },
    {
      "name": ":white_square_button:",
      "description": ":white_square_button: 🔳"
    },
    {
      "name": ":wilted_flower:",
      "description": ":wilted_flower: 🥀"
    },
    {
      "name": ":wind_chime:",
      "description": ":wind_chime: 🎐"
    },
    {
      "name": ":wind_face:",
      "description": ":wind_face: 🌬"
    },
    {
      "name": ":window:",
      "description": ":window: 🪟"
    },
    {
      "name": ":wine_glass:",
      "description": ":wine_glass: 🍷"
    },
    {
      "name": ":wink:",
      "description": ":wink: 😉"
    },
    {
      "name": ":wolf:",
      "description": ":wolf: 🐺"
    },
    {
      "name": ":woman:",
      "description": ":woman: 👩"
    },
    {
      "name": ":woman_artist:",
      "description": ":woman_artist: 👩🎨"
    },
    {
      "name": ":woman_astronaut:",
      "description": ":woman_astronaut: 👩🚀"
    },
    {
      "name": ":woman_beard:",
      "description": ":woman_beard: 🧔♀"
    },
    {
      "name": ":woman_cartwheeling:",
      "description": ":woman_cartwheeling: 🤸♀"
    },
    {
      "name": ":woman_cook:",
      "description": ":woman_cook: 👩🍳"
    },
    {
      "name": ":woman_dancing:",
      "description": ":woman_dancing: 💃"
    },
    {
      "name": ":woman_facepalming:",
      "description": ":woman_facepalming: 🤦♀"
    },
    {
      "name": ":woman_factory_worker:",
      "description": ":woman_factory_worker: 👩🏭"
    },
    {
      "name": ":woman_farmer:",
      "description": ":woman_farmer: 👩🌾"
    },
    {
      "name": ":woman_feeding_baby:",
      "description": ":woman_feeding_baby: 👩🍼"
    },
    {
      "name": ":woman_firefighter:",
      "description": ":woman_firefighter: 👩🚒"
    },
    {
      "name": ":woman_health_worker:",
      "description": ":woman_health_worker: 👩⚕"
    },
    {
      "name": ":woman_in_manual_wheelchair:",
      "description": ":woman_in_manual_wheelchair: 👩🦽"
    },
    {
      "name": ":woman_in_motorized_wheelchair:",
      "description": ":woman_in_motorized_wheelchair: 👩🦼"
    },
    {
      "name": ":woman_in_tuxedo:",
      "description": ":woman_in_tuxedo: 🤵♀"
    },
    {
      "name": ":woman_judge:",
      "description": ":woman_judge: 👩⚖"
    },
    {
      "name": ":woman_juggling:",
      "description": ":woman_juggling: 🤹♀"
    },
    {
      "name": ":woman_mechanic:",
      "description": ":woman_mechanic: 👩🔧"
    },
    {
      "name": ":woman_office_worker:",
      "description": ":woman_office_worker: 👩💼"
    },
    {
      "name": ":woman_pilot:",
      "description": ":woman_pilot: 👩✈"
    },
    {
      "name": ":woman_playing_handball:",
      "description": ":woman_playing_handball: 🤾♀"
    },
    {
      "name": ":woman_playing_water_polo:",
      "description": ":woman_playing_water_polo: 🤽♀"
    },
    {
      "name": ":woman_scientist:",
      "description": ":woman_scientist: 👩🔬"
    },
    {
      "name": ":woman_shrugging:",
      "description": ":woman_shrugging: 🤷♀"
    },
    {
      "name": ":woman_singer:",
      "description": ":woman_singer: 👩🎤"
    },
    {
      "name": ":woman_student:",
      "description": ":woman_student: 👩🎓"
    },
    {
      "name": ":woman_teacher:",
      "description": ":woman_teacher: 👩🏫"
    },
    {
      "name": ":woman_technologist:",
      "description": ":woman_technologist: 👩💻"
    },
    {
      "name": ":woman_with_headscarf:",
      "description": ":woman_with_headscarf: 🧕"
    },
    {
      "name": ":woman_with_probing_cane:",
      "description": ":woman_with_probing_cane: 👩🦯"
    },
    {
      "name": ":woman_with_turban:",
      "description": ":woman_with_turban: 👳♀"
    },
    {
      "name": ":woman_with_veil:",
      "description": ":woman_with_veil: 👰♀"
    },
    {
      "name": ":womans_clothes:",
      "description": ":womans_clothes: 👚"
    },
    {
      "name": ":womans_hat:",
      "description": ":womans_hat: 👒"
    },
    {
      "name": ":women_wrestling:",
      "description": ":women_wrestling: 🤼♀"
    },
    {
      "name": ":womens:",
      "description": ":womens: 🚺"
    },
    {
      "name": ":wood:",
      "description": ":wood: 🪵"
    },
    {
      "name": ":woozy_face:",
      "description": ":woozy_face: 🥴"
    },
    {
      "name": ":world_map:",
      "description": ":world_map: 🗺"
    },
    {
      "name": ":worm:",
      "description": ":worm: 🪱"
    },
    {
      "name": ":worried:",
      "description": ":worried: 😟"
    },
    {
      "name": ":wrench:",
      "description": ":wrench: 🔧"
    },
    {
      "name": ":wrestling:",
      "description": ":wrestling: 🤼"
    },
    {
      "name": ":writing_hand:",
      "description": ":writing_hand: ✍"
    },
    {
      "name": ":x:",
      "description": ":x: ❌"
    },
    {
      "name": ":yarn:",
      "description": ":yarn: 🧶"
    },
    {
      "name": ":yawning_face:",
      "description": ":yawning_face: 🥱"
    },
    {
      "name": ":yellow_circle:",
      "description": ":yellow_circle: 🟡"
    },
    {
      "name": ":yellow_heart:",
      "description": ":yellow_heart: 💛"
    },
    {
      "name": ":yellow_square:",
      "description": ":yellow_square: 🟨"
    },
    {
      "name": ":yemen:",
      "description": ":yemen: 🇾🇪"
    },
    {
      "name": ":yen:",
      "description": ":yen: 💴"
    },
    {
      "name": ":yin_yang:",
      "description": ":yin_yang: ☯"
    },
    {
      "name": ":yo_yo:",
      "description": ":yo_yo: 🪀"
    },
    {
      "name": ":yum:",
      "description": ":yum: 😋"
    },
    {
      "name": ":zambia:",
      "description": ":zambia: 🇿🇲"
    },
    {
      "name": ":zany_face:",
      "description": ":zany_face: 🤪"
    },
    {
      "name": ":zap:",
      "description": ":zap: ⚡"
    },
    {
      "name": ":zebra:",
      "description": ":zebra: 🦓"
    },
    {
      "name": ":zero:",
      "description": ":zero: 0⃣"
    },
    {
      "name": ":zimbabwe:",
      "description": ":zimbabwe: 🇿🇼"
    },
    {
      "name": ":zipper_mouth_face:",
      "description": ":zipper_mouth_face: 🤐"
    },
    {
      "name": ":zombie:",
      "description": ":zombie: 🧟"
    },
    {
      "name": ":zombie_man:",
      "description": ":zombie_man: 🧟♂"
    },
    {
      "name": ":zombie_woman:",
      "description": ":zombie_woman: 🧟♀"
    },
    {
      "name": ":zzz:",
      "description": ":zzz: 💤"
    }
  ];

  window.emojiCompleter = {
    triggerCharacters: [':'],
    insertMatch: function(editor, data) {
      console.log(data);
    },
    getCompletions: function(editor, session, pos, prefix, callback) {
      if (session.$mode && session.$mode.$id === 'ace/mode/markdown') {
        callback(null, emojiTable.map(function (table) {
          var token = session.getTokenAt(pos.row, pos.column);
          return {
            caption: table.description,
            value: token ? table.name.replace(token.value, "") : table.name,
            meta: "Emoji"
          };
        }));
      }
    },
    id: "emojiCompleter"
  };
})(window);