# Waterloo Rocketry OpenRocket

OpenRocket, and our code for running monte carlo simulations in OpenRocket.

## Dependencies
- Java 17 (JDK 17, Required for OpenRocket)
- IntelliJ IDEA (Recommended for development)
- OpenRocket 24.12.RC.01 (Included as a submodule)

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
 - OpenRocket GUI: This is used to run OpenRocket by itself.
- Gradle [run]: This is what you will be using to run our monte carlo software.
- Gradle [jar]: This can be used to build a JAR file for OpenRocket. You will likely not need to use this
- Gradle [build]: This will build OpenRocket from source, which is dependent auto run on the monte carlo software.
- Gradle [clean]: This will clean the build directory.