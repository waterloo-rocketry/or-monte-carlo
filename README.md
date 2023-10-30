# Waterloo Rocketry OpenRocket

OpenRocket, ORHelper, and associated code for our simulations

## Setup

Start by cloning this repository:

```sh
git clone --recurse-submodules https://github.com/waterloo-rocketry/openrocket
```

(If you did not use `--recurse-submodules`, run `git submodule update --init`. See https://git-scm.com/book/en/v2/Git-Tools-Submodules for details.)

Next, you will need to update the data files, which are a data file in the openrocket submodule. Run:
```sh
cd openrocket && git submodule update --init && cd ..
```

### IntelliJ

We will continue using [IntelliJ IDEA](https://www.jetbrains.com/idea/) IDE. Open the project.

You will need to configure your project JDK in File > Project Structure > Project. We will use a JDK 11 with language level 11.
If you don't have one installed, you will be able to download one here.

In the project, you will see a run configuration called "Build and Run OpenRocket".
You can use the Run/Debug buttons to build and run OpenRocket.
