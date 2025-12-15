import os
import argparse
import shutil
import sys

# Import your existing app setup to access DB and config
from app import app, db, AppEntry, UPLOAD_FOLDER, ICON_FOLDER
from apk_utils import parse_apk


def add_apk_local(file_path):
    # 1. Verify file exists
    if not os.path.isfile(file_path):
        print(f"❌ Error: File not found: {file_path}")
        sys.exit(1)

    if not file_path.endswith(".apk"):
        print("❌ Error: File must have .apk extension")
        sys.exit(1)

    print(f"📦 Processing: {file_path}")

    # 2. Parse Metadata using your existing utility
    metadata = parse_apk(file_path)
    if not metadata:
        print("❌ Error: Failed to parse APK. Is the file corrupted?")
        sys.exit(1)

    pkg = metadata["package_name"]
    print(f"   Name: {metadata['name']}")
    print(f"   Package: {pkg}")
    print(f"   Version: {metadata['version_name']} (Code: {metadata['version_code']})")

    # 3. Open App Context to interact with Database
    with app.app_context():
        existing_app = AppEntry.query.filter_by(package_name=pkg).first()

        # Version Control Logic
        if existing_app:
            if metadata["version_code"] <= existing_app.version_code:
                print(
                    f"⚠️  Skipped: Database version ({existing_app.version_code}) is newer or equal to input."
                )
                sys.exit(0)
            print(f"🔄 Updating existing app (Old Ver: {existing_app.version_code})...")
        else:
            print("✨ Creating new app entry...")

        # 4. File Operations (Copying local file instead of saving stream)
        final_filename = f"{pkg}.apk"
        final_path = os.path.join(UPLOAD_FOLDER, final_filename)

        try:
            # shutil.copy2 preserves metadata (timestamps)
            shutil.copy2(file_path, final_path)
            print(f"   Filesystem: APK saved to {final_path}")
        except Exception as e:
            print(f"❌ Error copying APK file: {e}")
            sys.exit(1)

        # 5. Save Icon
        if metadata["icon_image"]:
            icon_path = os.path.join(ICON_FOLDER, f"{pkg}.png")
            try:
                metadata["icon_image"].save(icon_path, "PNG")
                print(f"   Filesystem: Icon saved to {icon_path}")
            except Exception as e:
                print(f"⚠️  Warning: Failed to save icon: {e}")

        # 6. Update Database
        if existing_app:
            existing_app.name = metadata["name"]
            existing_app.version_code = metadata["version_code"]
            existing_app.version_name = metadata["version_name"]
            existing_app.filename = final_filename
        else:
            new_app = AppEntry(
                package_name=pkg,
                name=metadata["name"],
                version_code=metadata["version_code"],
                version_name=metadata["version_name"],
                filename=final_filename,
            )
            db.session.add(new_app)

        db.session.commit()
        print("✅ Success! Database updated.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Manually add an APK to the App Store."
    )
    parser.add_argument("apk_path", help="Path to the APK file on the server")

    args = parser.parse_args()

    add_apk_local(args.apk_path)
