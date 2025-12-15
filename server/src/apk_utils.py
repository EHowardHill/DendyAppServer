import os
from androguard.core.bytecodes.apk import APK
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

        # Extract Icon
        # Androguard returns the raw bytes of the icon file
        icon_data = apk.get_icon()
        icon_image = None

        if icon_data:
            icon_image = Image.open(io.BytesIO(icon_data))

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
