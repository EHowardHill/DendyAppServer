import os
from androguard.core.apk import APK
from PIL import Image
import io

def parse_apk(file_path):
    """
    Extracts metadata and icon from an APK file.
    """
    try:
        apk = APK(file_path)

        # Extract core metadata
        package_name = apk.get_package()
        app_name = apk.get_app_name()
        version_code = int(apk.get_androidversion_code())
        version_name = apk.get_androidversion_name()

        # --- FIX STARTS HERE ---
        icon_image = None
        try:
            # 1. Get the path to the icon file inside the APK (e.g., "res/drawable/icon.png")
            # Androguard tries to find the highest resolution icon automatically.
            icon_path = apk.get_app_icon()

            # 2. If a path was found, extract the raw bytes of that file
            if icon_path:
                icon_data = apk.get_file(icon_path)
                
                # 3. Convert bytes to an Image object
                if icon_data:
                    icon_image = Image.open(io.BytesIO(icon_data))
        except Exception as e:
            print(f"Warning: Could not extract icon: {e}")
        # --- FIX ENDS HERE ---

        return {
            "package_name": package_name,
            "name": app_name,
            "version_code": version_code,
            "version_name": version_name,
            "icon_image": icon_image,
        }
    except Exception as e:
        print(f"Error parsing APK: {e}")
        return None