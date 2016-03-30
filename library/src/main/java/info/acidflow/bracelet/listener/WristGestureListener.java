package info.acidflow.bracelet.listener;

/**
 * A WristGestureListener receives notification from an WristGestureDetector.
 * Notification indicate when a gesture such as a wrist in / out has been detected.
 */
public interface WristGestureListener {

    /**
     * Called when a wrist out (scroll down) gesture has been detected
     */
    void onWristOut();

    /**
     * Called when a wrist in (scroll up) gesture has been detected
     */
    void onWristIn();

    /**
     * Called when an arm down (tap) gesture has been detected
     */
    void onArmDown();

    /**
     * Called when an arm up (back) gesture has been detected
     */
    void onArmUp();
}
