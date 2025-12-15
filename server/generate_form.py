import os

final = ""

for file in os.listdir("src"):

    if file not in []:

        with open(os.path.join("src", file), "r") as f:
            final += file + ": \n" + f.read() + "\n\n"

with open("source.txt", "w") as f:
    f.write(final.strip())