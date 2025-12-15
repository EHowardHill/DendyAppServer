import os

final = ""

for file in os.listdir("app/src/main/res/layout/"):

    if file not in []:

        with open(os.path.join("app/src/main/res/layout/", file), "r") as f:
            final += file + ": \n" + f.read() + "\n\n"

for file in os.listdir("app/src/main/java/com/dendy/market/"):

    if file not in []:

        with open(os.path.join("app/src/main/java/com/dendy/market/", file), "r") as f:
            final += file + ": \n" + f.read() + "\n\n"

with open("source.txt", "w") as f:
    f.write(final.strip())
