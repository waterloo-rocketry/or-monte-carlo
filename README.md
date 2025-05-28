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

You will need to configure your project JDK in File > Project Structure > Project. We will use Java 17. 
Intellij should recognize and download this automatically.

In the top-right of the project, you will see a few gradle tasks. Here are the three important ones:
- `./gradlew buildOpenRocket`: This builds OpenRocket. **Run this first before running the plugin**!
- `./gradlew runOpenRocket`: This is used to run OpenRocket by itself.
- `./gradlew run`: This is used to run the Monte-Carlo plugin
- `./gradlew buildExtensions`: This will build all extensions in the extensions directory. Run this first if you want extensions