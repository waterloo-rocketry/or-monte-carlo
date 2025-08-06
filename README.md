# Waterloo Rocketry OpenRocket

OpenRocket, and our code for running monte carlo simulations in OpenRocket.

## Requirements

- Java 17

## Running the Simulator

Download the latest release from
the [releases page](https://github.com/waterloo-rocketry/or-monte-carlo/releases/latest)

In the downloaded directory run `java -jar WaterlooRocketry-OpenRocket-{version}-all.jar`

## Configuration

Create a file named `config.toml` in the same directory as the jar with the structure as follows:

```toml
[simulation]

[simulation.executor]
batch_size = 30

[simulation.options]
launch_latitude = 47.965378
launch_longitude = -81.873536
launch_altitude = 420.0144
launch_rod_length = 9.144
launch_into_wind = false
launch_rod_angle = 0.0872665
launch_rod_direction = 4.71239
max_simulation_time = 2400
```

The above is the default configuration if no configuration file is provided.
All measurements are in SI units.

## Development

### Setup

#### Dependencies

- Java 17 (JDK 17, Required for OpenRocket)
- IntelliJ IDEA (Recommended for development)
- OpenRocket 24.12.RC.01 (Included as a submodule)

Start by cloning this repository:

```sh
git clone --recurse-submodules https://github.com/waterloo-rocketry/openrocket
```

(If you did not use `--recurse-submodules`, run `git submodule update --init --recursive`. See
https://git-scm.com/book/en/v2/Git-Tools-Submodules for details.)

#### IntelliJ

We will continue using [IntelliJ IDEA](https://www.jetbrains.com/idea/) IDE. Open the project.
You do not need to install a JDK or Ant separately.

You will need to configure your project JDK in File > Project Structure > Project. We will use Java 17.
Intellij should recognize and download this automatically.

In the top-right of the project, you will see a few gradle tasks. Here are the three important ones:

- `./gradlew buildOpenRocket`: This builds OpenRocket. **Run this first before running the plugin**!
- `./gradlew runOpenRocket`: This is used to run OpenRocket by itself.
- `./gradlew run`: This is used to run the Monte-Carlo plugin
- `./gradlew buildExtensions`: This will build all extensions in the extensions directory. Run this first if you want
  extensions

To get debug logging from OpenRocket and the extension, add `-Dlog-level='DEBUG'` to the run command.

### Release Process

Follow these steps to release a new version of the Monte-Carlo OR plugin:

#### 1. Update the Version Number

Edit the [build.gradle](build.gradle) file to reflect the new version:

```gradle
group 'com.waterloorocketry'
version 'X.Y.Z' // Format: Major.Minor.Hotfix
```

Example: Change `version '1.0.0'` to `version '1.1.0'` for a minor update.

#### 2. Verify CI Build

Ensure the following CI workflow is successful on the `main` branch:

- CI – Build Monte-Carlo or-plugin

You can check this in your CI/CD platform (e.g., GitHub Actions, Jenkins, etc.).

#### 3. Trigger the Release

Initiate the release by running the following workflow on the `main` branch:

- Manual Release – Monte-Carlo or-plugin

This step will publish the current build based on the version specified in `build.gradle`.
