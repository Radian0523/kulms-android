"""Generate Google Play Store promotional graphics.

出力:
- feature_graphic.png         (1024 x 500)  フィーチャーグラフィック
- app_icon.png                (512 x 512)   アプリアイコン
- phone_01_assignment.png     (1080 x 1920) 9:16 スマホ用
- phone_02_settings.png       (1080 x 1920) 9:16 スマホ用
- tablet7_01_assignment.png   (1920 x 1080) 16:9 7インチタブレット
- tablet7_02_settings.png     (1920 x 1080) 16:9 7インチタブレット
- tablet10_01_assignment.png  (2560 x 1440) 16:9 10インチタブレット
- tablet10_02_settings.png    (2560 x 1440) 16:9 10インチタブレット
"""

from PIL import Image, ImageDraw, ImageFont, ImageFilter
import numpy as np
import os

OUTPUT_DIR = os.path.dirname(os.path.abspath(__file__))

FONT_BOLD = "/System/Library/Fonts/ヒラギノ角ゴシック W6.ttc"
FONT_REGULAR = "/System/Library/Fonts/ヒラギノ角ゴシック W4.ttc"

SRC_01 = os.path.expanduser("~/Downloads/IMG_0257.PNG")
SRC_02 = os.path.expanduser("~/Downloads/IMG_0258.PNG")

# 画面1: 課題一覧（青系）
BG1_TOP = (41, 98, 255)
BG1_BOT = (88, 166, 255)
TITLE1 = "全科目の課題を\nひと目で確認"
SUB1 = "緊急度別に色分け表示"

# 画面2: 通知設定（紫系）
BG2_TOP = (109, 58, 230)
BG2_BOT = (170, 120, 255)
TITLE2 = "通知タイミングを\n自由にカスタマイズ"
SUB2 = "10分前〜3日前から最大5個設定"


def make_gradient(width, height, top, bottom):
    arr = np.zeros((height, width, 3), dtype=np.uint8)
    for c in range(3):
        arr[:, :, c] = np.linspace(top[c], bottom[c], height, dtype=np.uint8)[:, None]
    return Image.fromarray(arr)


def make_gradient_horizontal(width, height, left, right):
    arr = np.zeros((height, width, 3), dtype=np.uint8)
    for c in range(3):
        arr[:, :, c] = np.linspace(left[c], right[c], width, dtype=np.uint8)[None, :]
    return Image.fromarray(arr)


def add_rounded_corners(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([(0, 0), img.size], radius=radius, fill=255)
    result = Image.new("RGBA", img.size, (0, 0, 0, 0))
    result.paste(img, mask=mask)
    return result


def add_shadow(img, offset=(0, 15), blur_radius=40, opacity=60):
    shadow = Image.new(
        "RGBA", (img.width + blur_radius * 2, img.height + blur_radius * 2), (0, 0, 0, 0)
    )
    inner = Image.new("RGBA", img.size, (0, 0, 0, opacity))
    if img.mode == "RGBA":
        inner.putalpha(img.split()[3].point(lambda p: min(p, opacity)))
    shadow.paste(inner, (blur_radius + offset[0], blur_radius + offset[1]))
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur_radius))
    return shadow, blur_radius


def draw_centered_text(draw, text, y, width, font, fill="white", line_height=None):
    if line_height is None:
        line_height = int(font.size * 1.25)
    for line in text.split("\n"):
        bbox = font.getbbox(line)
        lw = bbox[2] - bbox[0]
        draw.text(((width - lw) / 2, y), line, fill=fill, font=font)
        y += line_height
    return y


# ============ 1. フィーチャーグラフィック (1024x500) ============
def gen_feature_graphic():
    W, H = 1024, 500
    bg = make_gradient_horizontal(W, H, (41, 98, 255), (109, 58, 230)).convert("RGBA")
    draw = ImageDraw.Draw(bg)

    # 左側にテキスト
    title_font = ImageFont.truetype(FONT_BOLD, 78)
    sub_font = ImageFont.truetype(FONT_REGULAR, 36)

    draw.text((60, 130), "KULMS+", fill="white", font=title_font)
    draw.text(
        (60, 230),
        "京都大学LMS 課題管理アプリ",
        fill=(255, 255, 255, 230),
        font=sub_font,
    )
    draw.text(
        (60, 290),
        "全科目の課題・テストを一覧管理",
        fill=(255, 255, 255, 200),
        font=sub_font,
    )
    draw.text(
        (60, 350),
        "通知タイミングを自由にカスタマイズ",
        fill=(255, 255, 255, 200),
        font=sub_font,
    )

    # 右側に縮小デバイスモックアップ
    ss = Image.open(SRC_01).convert("RGBA")
    target_h = int(H * 0.85)
    scale = target_h / ss.height
    target_w = int(ss.width * scale)
    ss = ss.resize((target_w, target_h), Image.LANCZOS)
    ss = add_rounded_corners(ss, 25)

    shadow, blur_r = add_shadow(ss, offset=(0, 10), blur_radius=25, opacity=80)

    ss_x = W - target_w - 80
    ss_y = (H - target_h) // 2
    bg.paste(shadow, (ss_x - blur_r, ss_y - blur_r), shadow)
    bg.paste(ss, (ss_x, ss_y), ss)

    out = os.path.join(OUTPUT_DIR, "feature_graphic.png")
    bg.convert("RGB").save(out, "PNG", optimize=True)
    print(f"Saved: {out} ({W}x{H})")


# ============ 2. アプリアイコン (512x512) ============
def gen_app_icon():
    W, H = 512, 512
    bg_color = (25, 118, 210)  # #1976D2
    img = Image.new("RGBA", (W, H), bg_color + (255,))
    draw = ImageDraw.Draw(img)

    # "K" letter - ic_launcher_foreground.xml のパスを 108dp → 512px に拡大
    # 元のpathData: M38,34 L46,34 L46,49 L58,34 L67,34 L53,50 L68,74 L59,74 L47,55 L46,56 L46,74 L38,74 Z
    scale = W / 108.0
    path = [
        (38, 34), (46, 34), (46, 49), (58, 34), (67, 34),
        (53, 50), (68, 74), (59, 74), (47, 55), (46, 56),
        (46, 74), (38, 74),
    ]
    scaled = [(x * scale, y * scale) for x, y in path]
    draw.polygon(scaled, fill="white")

    out = os.path.join(OUTPUT_DIR, "app_icon.png")
    img.save(out, "PNG", optimize=True)
    print(f"Saved: {out} ({W}x{H})")


# ============ 3. スマホ用 (1080x1920, 9:16) ============
def gen_phone(src, title, subtitle, bg_top, bg_bot, output):
    W, H = 1080, 1920
    bg = make_gradient(W, H, bg_top, bg_bot).convert("RGBA")
    draw = ImageDraw.Draw(bg)

    title_font = ImageFont.truetype(FONT_BOLD, 76)
    sub_font = ImageFont.truetype(FONT_REGULAR, 42)

    y = 140
    y = draw_centered_text(draw, title, y, W, title_font, line_height=96)
    y += 20
    draw_centered_text(draw, subtitle, y, W, sub_font, fill=(255, 255, 255, 220))

    ss = Image.open(src).convert("RGBA")
    target_w = int(W * 0.74)
    scale = target_w / ss.width
    target_h = int(ss.height * scale)
    ss = ss.resize((target_w, target_h), Image.LANCZOS)
    ss = add_rounded_corners(ss, 36)

    shadow, blur_r = add_shadow(ss, offset=(0, 15), blur_radius=40, opacity=60)

    ss_x = (W - target_w) // 2
    ss_y = H - target_h + int(target_h * 0.06)  # 少し下に見切れる
    bg.paste(shadow, (ss_x - blur_r, ss_y - blur_r), shadow)
    bg.paste(ss, (ss_x, ss_y), ss)

    out = os.path.join(OUTPUT_DIR, output)
    bg.convert("RGB").save(out, "PNG", optimize=True)
    print(f"Saved: {out} ({W}x{H})")


# ============ 4. タブレット (16:9 横長) ============
def gen_tablet(W, H, src, title, subtitle, bg_top, bg_bot, output, title_size, sub_size):
    bg = make_gradient(W, H, bg_top, bg_bot).convert("RGBA")
    draw = ImageDraw.Draw(bg)

    # 左側にテキスト、右側にデバイス
    title_font = ImageFont.truetype(FONT_BOLD, title_size)
    sub_font = ImageFont.truetype(FONT_REGULAR, sub_size)

    # テキスト (左半分の中央付近に配置)
    text_x_margin = int(W * 0.07)
    text_area_w = int(W * 0.5) - text_x_margin
    text_block_h = 0
    for line in title.split("\n"):
        text_block_h += int(title_size * 1.25)
    text_block_h += sub_size + 40  # subtitle + gap
    text_y = (H - text_block_h) // 2

    y = text_y
    for line in title.split("\n"):
        draw.text((text_x_margin, y), line, fill="white", font=title_font)
        y += int(title_size * 1.25)
    y += 30
    draw.text((text_x_margin, y), subtitle, fill=(255, 255, 255, 220), font=sub_font)

    # デバイスモックアップ（右半分の中央）
    ss = Image.open(src).convert("RGBA")
    target_h = int(H * 0.82)
    scale = target_h / ss.height
    target_w = int(ss.width * scale)
    ss = ss.resize((target_w, target_h), Image.LANCZOS)
    corner_r = int(36 * (H / 1920))
    ss = add_rounded_corners(ss, corner_r)

    blur_r = int(40 * (H / 1920))
    shadow, blur_r = add_shadow(ss, offset=(0, 15), blur_radius=blur_r, opacity=60)

    ss_x = int(W * 0.58)
    ss_y = (H - target_h) // 2
    bg.paste(shadow, (ss_x - blur_r, ss_y - blur_r), shadow)
    bg.paste(ss, (ss_x, ss_y), ss)

    out = os.path.join(OUTPUT_DIR, output)
    bg.convert("RGB").save(out, "PNG", optimize=True)
    print(f"Saved: {out} ({W}x{H})")


if __name__ == "__main__":
    # 1. フィーチャーグラフィック
    gen_feature_graphic()

    # 2. アプリアイコン
    gen_app_icon()

    # 3. スマホ用 (1080x1920)
    gen_phone(SRC_01, TITLE1, SUB1, BG1_TOP, BG1_BOT, "phone_01_assignment.png")
    gen_phone(SRC_02, TITLE2, SUB2, BG2_TOP, BG2_BOT, "phone_02_settings.png")

    # 4. 7インチタブレット (1920x1080)
    gen_tablet(1920, 1080, SRC_01, TITLE1, SUB1, BG1_TOP, BG1_BOT,
               "tablet7_01_assignment.png", title_size=72, sub_size=38)
    gen_tablet(1920, 1080, SRC_02, TITLE2, SUB2, BG2_TOP, BG2_BOT,
               "tablet7_02_settings.png", title_size=72, sub_size=38)

    # 5. 10インチタブレット (2560x1440)
    gen_tablet(2560, 1440, SRC_01, TITLE1, SUB1, BG1_TOP, BG1_BOT,
               "tablet10_01_assignment.png", title_size=96, sub_size=50)
    gen_tablet(2560, 1440, SRC_02, TITLE2, SUB2, BG2_TOP, BG2_BOT,
               "tablet10_02_settings.png", title_size=96, sub_size=50)

    print("Done!")
