# MT-SER workload generator

## build and run

Build the workload generator with maven.

```
$ mvn install
```

Run the generator with java.

```
java -ea -jar target/txnTest-1-jar-with-dependencies.jar local config.yaml
```

You can find a typical config.yaml for MT-SER workload generator in [history directory](../../../History/figures/fig_8_a/n8_k200_t40000_kd0_pre/mini-config.yaml).