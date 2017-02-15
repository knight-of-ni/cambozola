# cambozola
Cambozola V0.936 (Java Plugin for MJPEG Streams)

This repository is a snapshot of code downloaded from [author's website](http://www.charliemouse.com/code/cambozola/) on Feb 13, 2017

A Dockerfile exists to allow for compilation (via Ant) in a Docker container so that older JDK's may be used. In this case, it is compiled using Oracle JDK6
The Dockerfile uses `dockerfile/ubuntu` as a base image that can be built from this [repository](https://github.com/dockerfile/ubuntu)

Assuming Cambozola source is in ~/Dev/cambozola run the container, then run ant and exit

```
docker run -v ~/Dev/cambozola:/src/cambozola --rm -it dockerfile/oracle-java6
```

