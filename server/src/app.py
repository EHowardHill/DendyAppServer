import os
from flask import Flask, request, jsonify, send_from_directory, abort
from flask_sqlalchemy import SQLAlchemy
from apk_utils import parse_apk

app = Flask(__name__)

# --- Configuration ---
BASE_DIR = os.path.abspath(os.path.dirname(__file__))
UPLOAD_FOLDER = os.path.join("..", "app_store", "apps")
ICON_FOLDER = os.path.join("..", "app_store", "icons")
app.config["SQLALCHEMY_DATABASE_URI"] = "sqlite:///store.db"
app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False
app.config["MAX_CONTENT_LENGTH"] = 100 * 1024 * 1024

# Ensure directories exist
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs(ICON_FOLDER, exist_ok=True)

db = SQLAlchemy(app)


# --- Database Model ---
class AppEntry(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    package_name = db.Column(db.String(150), unique=True, nullable=False)
    name = db.Column(db.String(100), nullable=False)
    version_code = db.Column(db.Integer, nullable=False)
    version_name = db.Column(db.String(50), nullable=False)
    filename = db.Column(db.String(200), nullable=False)

    def to_dict(self):
        return {
            "name": self.name,
            "package_name": self.package_name,
            "version": self.version_name,
            "version_code": self.version_code,
            "download_url": f"/download/{self.package_name}",
            "icon_url": f"/icon/{self.package_name}",
        }


# Initialize DB
with app.app_context():
    db.create_all()

# --- API Endpoints ---


@app.route("/api/list", methods=["GET"])
def list_apps():
    """Returns a JSON list of all available apps."""
    apps = AppEntry.query.all()
    return jsonify([app.to_dict() for app in apps])


@app.route("/upload", methods=["POST"])
def upload_apk():
    """
    Admin endpoint to upload an APK.
    Usage: curl -F "file=@/path/to/app.apk" http://localhost:5000/upload
    """
    if "file" not in request.files:
        return jsonify({"error": "No file part"}), 400

    file = request.files["file"]
    if file.filename == "":
        return jsonify({"error": "No selected file"}), 400

    if file and file.filename.endswith(".apk"):
        # 1. Save temporarily to parse
        temp_path = os.path.join(UPLOAD_FOLDER, "temp.apk")
        file.save(temp_path)

        # 2. Parse Metadata
        metadata = parse_apk(temp_path)
        if not metadata:
            os.remove(temp_path)
            return jsonify({"error": "Failed to parse APK"}), 400

        pkg = metadata["package_name"]

        # 3. Check for existing app
        existing_app = AppEntry.query.filter_by(package_name=pkg).first()

        # Version Control Logic
        if existing_app and metadata["version_code"] <= existing_app.version_code:
            os.remove(temp_path)
            return (
                jsonify(
                    {
                        "error": "Version provided is older or equal to current version",
                        "current": existing_app.version_code,
                        "uploaded": metadata["version_code"],
                    }
                ),
                409,
            )

        # 4. Save Final File (Renamed to package_name.apk for consistency)
        final_filename = f"{pkg}.apk"
        final_path = os.path.join(UPLOAD_FOLDER, final_filename)
        os.replace(temp_path, final_path)

        # 5. Save Icon
        if metadata["icon_image"]:
            icon_path = os.path.join(ICON_FOLDER, f"{pkg}.png")
            metadata["icon_image"].save(icon_path, "PNG")

        # 6. Update Database
        if existing_app:
            existing_app.name = metadata["name"]
            existing_app.version_code = metadata["version_code"]
            existing_app.version_name = metadata["version_name"]
            existing_app.filename = final_filename
            action = "updated"
        else:
            new_app = AppEntry(
                package_name=pkg,
                name=metadata["name"],
                version_code=metadata["version_code"],
                version_name=metadata["version_name"],
                filename=final_filename,
            )
            db.session.add(new_app)
            action = "created"

        db.session.commit()

        return jsonify(
            {
                "status": "success",
                "action": action,
                "app": metadata["name"],
                "version": metadata["version_name"],
            }
        )

    return jsonify({"error": "Invalid file type"}), 400


@app.route("/download/<package_name>")
def download_apk(package_name):
    """Serves the APK file."""
    app_entry = AppEntry.query.filter_by(package_name=package_name).first_or_404()
    return send_from_directory(UPLOAD_FOLDER, app_entry.filename, as_attachment=True)


@app.route("/icon/<package_name>")
def get_icon(package_name):
    """Serves the App Icon."""
    # Check if custom icon exists, else serve a default placeholder (optional logic)
    filename = f"{package_name}.png"
    if not os.path.exists(os.path.join(ICON_FOLDER, filename)):
        return jsonify({"error": "Icon not found"}), 404
    return send_from_directory(ICON_FOLDER, filename)


@app.route("/api/updates", methods=["POST"])
def check_updates():
    client_manifest = request.get_json()

    if not client_manifest:
        return jsonify({"error": "Invalid JSON payload"}), 400

    updates_available = []

    # Iterate through the apps installed on the user's device
    for pkg_name, client_version_code in client_manifest.items():
        # Find the app in our store database
        app_entry = AppEntry.query.filter_by(package_name=pkg_name).first()

        # LOGIC: If app exists in DB AND DB version > Client version
        if app_entry and app_entry.version_code > int(client_version_code):
            updates_available.append(
                {
                    "package_name": app_entry.package_name,
                    "name": app_entry.name,
                    "current_version": client_version_code,  # What they have
                    "new_version": app_entry.version_code,  # What we have
                    "version_name": app_entry.version_name,  # Human readable string
                    "download_url": f"/download/{app_entry.package_name}",
                    "icon_url": f"/icon/{app_entry.package_name}",
                }
            )

    return jsonify(updates_available)


if __name__ == "__main__":
    # Host 0.0.0.0 allows access from other devices on the network
    app.run(debug=True, host="0.0.0.0", port=5000)
