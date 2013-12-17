labs-wayfinder-java
===================
This is a work-in-progress demo of a motion tracking and simple augmented reality application. Motion tracking is accomplished via OpenCV's BackgroundSubtractorMOG algorithm and a cheap webcam. The interface is built in Processing.

A "spotlight" is drawn around the largest detected moving object (assumed to be a person), and context-sensitive navigation guides to potential destinations (conference rooms, offices, exits, etc) are drawn based on the position of the person in 2D space.

This is very much still a work in progress.

A video demo can be seen here:
http://www.youtube.com/watch?v=9Ft3OACM6sU

More on OpenCV can be found here:
http://opencv.org/

More on Processing can be found here:
http://www.processing.org/
