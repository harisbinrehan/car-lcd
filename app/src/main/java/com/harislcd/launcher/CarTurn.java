package com.harislcd.launcher;

/**
 * The owner's own photos of the car from six sides (turn_0.png .. turn_5.png), in order round the car:
 * front, front right, right, rear, left, front left. Dragging a finger across the car steps through them.
 * Per photo: width, height, then the four corners of its blanked number plate (top left, top right,
 * bottom right, bottom left; zeros where the plate is not visible). Written by hand from tools/gen/turn.
 */
final class CarTurn {
    private CarTurn() {}

    static final float[][] VIEWS = {
            {698.0f, 660.0f, 296.3f, 534.5f, 438.9f, 533.1f, 434.8f, 590.4f, 291.6f, 592.5f},
            {788.0f, 660.0f, 634.8f, 497.3f, 717.3f, 463.2f, 709.2f, 516.4f, 621.9f, 556.6f},
            {900.0f, 574.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f},
            {771.0f, 660.0f, 323.2f, 321.8f, 463.0f, 318.3f, 464.4f, 385.4f, 326.7f, 391.0f},
            {900.0f, 492.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f},
            {744.0f, 660.0f, 80.7f, 461.1f, 185.8f, 509.5f, 194.6f, 567.5f, 99.1f, 531.4f},
    };
}
