# MTC-SER/SI

MTC is implemented based on [PolySI](https://github.com/amnore/PolySI).

## Build and run

Build with gradle wrapper.

```
$ ./gradlew jar
```

Run the MTC-SI checker by the `auditMini` subcommand.

```
$ java -jar build/libs/PolySI-1.0.0-SNAPSHOT.jar auditMini  -t=text history.txt
```

Run the MTC-SER checker by the `auditSER` subcommand.

```
$ java -jar build/libs/PolySI-1.0.0-SNAPSHOT.jar auditSER  -t=text history.txt
```

MTC accepts 3 types of history: text, cobra, dbcop.
