from orhelper import orhelper


def main():
    with orhelper.OpenRocketInstance() as instance:
        print(f"{instance.or_log_level=}")


if __name__ == "__main__":
    main()
