# Waterloo Rocketry OpenRocket

OpenRocket, ORHelper, and associated code for our simulations

## Setup

Start by cloning this repository:

```sh
git clone --recurse-submodules https://github.com/waterloo-rocketry/openrocket
```

(If you did not use `--recurse-submodules`, run `git submodule update --init --recursive`. See
https://git-scm.com/book/en/v2/Git-Tools-Submodules for details.)

### IntelliJ

We will continue using [IntelliJ IDEA](https://www.jetbrains.com/idea/) IDE. Open the project.
You do not need to install a JDK or Ant separately.

You will need to configure your project JDK in File > Project Structure > Project. We will use a JDK 11 with language
level 11. If you don't have one installed, you will be able to download one here.

In the top-right of the project, you will see three run configurations:
 - OpenRocket JAR: This can be used to build a JAR file for OpenRocket. You will likely not need to use this
 - OpenRocket GUI: This is used to run OpenRocket by itself.
 - OR Monte Carlo: This is what you will be using to run our monte carlo software.
