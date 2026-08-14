#!/usr/bin/env python3
"""Generate the Amazon Appstore asset package for Jellystack TV beta 3."""

from pathlib import Path
from shutil import copyfile

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "store-assets" / "amazon" / "0.16.0-tv-beta.3"


def save_opaque(source: Path, destination: Path, expected_size: tuple[int, int]) -> None:
    with Image.open(source) as image:
        if image.size != expected_size:
            raise ValueError(f"{source} is {image.size}, expected {expected_size}")
        if image.mode in {"RGBA", "LA"} or "transparency" in image.info:
            background = Image.new("RGB", image.size, "#08090f")
            alpha = image.getchannel("A") if "A" in image.getbands() else None
            background.paste(image.convert("RGB"), mask=alpha)
            image = background
        else:
            image = image.convert("RGB")
        destination.parent.mkdir(parents=True, exist_ok=True)
        image.save(destination, format="PNG", optimize=True)


def save_transparent_icon(source: Path, destination: Path, size: int) -> None:
    with Image.open(source) as image:
        image = image.convert("RGBA")
        alpha_bounds = image.getchannel("A").getbbox()
        if alpha_bounds is None:
            raise ValueError(f"{source} has no visible pixels")
        artwork = image.crop(alpha_bounds)
        max_artwork = round(size * 0.80)
        artwork.thumbnail((max_artwork, max_artwork), Image.Resampling.LANCZOS)
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        position = ((size - artwork.width) // 2, (size - artwork.height) // 2)
        canvas.alpha_composite(artwork, position)
        destination.parent.mkdir(parents=True, exist_ok=True)
        canvas.save(destination, format="PNG", optimize=True)


def main() -> None:
    tablet = OUTPUT / "tablet"
    fire_tv = OUTPUT / "fire-tv"

    foreground = ROOT / "app-android/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp"
    save_transparent_icon(foreground, tablet / "tablet_large_icon-512x512.png", 512)
    save_transparent_icon(foreground, tablet / "tablet_small_icon-114x114.png", 114)

    screenshot_root = (
        ROOT
        / "design-screenshots/src/screenshotTestDebug/reference/dev/jellystack/design/screenshots"
        / "JellystackResponsiveScreenshotTestKt"
    )
    tablet_sources = (
        ("HomeDark_Expanded tablet_4e987d63_0.png", "01-home-1920x1200.png"),
        ("DetailDark_Expanded tablet_4e987d63_0.png", "02-details-1920x1200.png"),
        ("DiscoverDark_Expanded tablet_4e987d63_0.png", "03-discover-1920x1200.png"),
    )
    for source_name, destination_name in tablet_sources:
        save_opaque(screenshot_root / source_name, tablet / destination_name, (1920, 1200))

    save_opaque(
        ROOT / "store-assets/tv/amazon-app-icon-1280x720.png",
        fire_tv / "app-icon-1280x720.png",
        (1280, 720),
    )
    save_opaque(
        ROOT / "store-assets/tv/amazon-background-1920x1080.png",
        fire_tv / "background-1920x1080.png",
        (1920, 1080),
    )

    fire_tv_sources = (
        ("beta3-home.png", "01-home-1920x1080.png"),
        ("beta3-row.png", "02-home-sections-1920x1080.png"),
        ("beta3-detail.png", "03-details-1920x1080.png"),
        ("beta3-rail.png", "04-navigation-1920x1080.png"),
        ("beta3-settings.png", "05-settings-1920x1080.png"),
    )
    for source_name, destination_name in fire_tv_sources:
        save_opaque(ROOT / "build" / source_name, fire_tv / destination_name, (1920, 1080))

    copyfile(ROOT / "LICENSE", OUTPUT / "LICENSE")


if __name__ == "__main__":
    main()
