package info.acidflow.bracelet;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import info.acidflow.bracelet.listener.WristGestureListener;

/**
 * A WristGestureDetector uses the sensors to detect wrist gestures.
 * It uses the linear acceleration sensor to detect arm gestures and the gyroscope to detect wrist
 * gestures.
 */
public class WristGestureDetector implements SensorEventListener {

    private static final String TAG = WristGestureDetector.class.getSimpleName();

    private static final int GESTURE_WRIST_OUT = 0;
    private static final int GESTURE_WRIST_IN = 1;
    private static final int GESTURE_ARM_DOWN = 2;
    private static final int GESTURE_ARM_UP = 3;

    private static final int AXIS_X = 0;
    private static final int AXIS_Y = 1;
    private static final int AXIS_Z = 2;

    private static final int THROTTLE_EVENT_TIME_MS = 1200;
    private static final float SIGNIFICANT_THRESHOLD_ACCELEROMETER = 6;
    private static final float SIGNIFICANT_THRESHOLD_GYROSCOPE = 10;

    private static final int MS_TO_NS = 1000000;

    private SensorManager mSensorManager;
    private Sensor mLinearAccelerationSensor;
    private Sensor mGyroscopeVectorSensor;

    private float mLastSignificantValue;
    private long mLastGestureDetectedTimestamp;

    private boolean isWristGestureInProgress;
    private boolean isArmGestureInProgress;

    private List< WristGestureListener > mWristGestureListeners;

    public WristGestureDetector( Context context ) {
        super();
        intializeSensors( context );
        isWristGestureInProgress = false;
        mWristGestureListeners = new ArrayList<>( 1 );
    }

    /**
     * Add a wrist gesture listener which will be called when a wrist gesture is detected
     *
     * @param listener the listener to be called
     * @return true if the listener has been added, false otherwise
     */
    public boolean addWristGestureListener( WristGestureListener listener ) {
        return mWristGestureListeners.add( listener );
    }

    /**
     * Remove a wrist gesture listener
     *
     * @param listener the listener to be removed
     * @return true if the listener has been removed, false otherwise
     */
    public boolean removeWristGestureListener( WristGestureListener listener ) {
        return mWristGestureListeners.remove( listener );
    }

    /**
     * Remove all wrist gesture listeners
     */
    public void removeAllWristGestureListener() {
        mWristGestureListeners.clear();
    }

    /**
     * Check is the device is able to detect arm down/up gestures
     *
     * @return true if the device can detect arm gestures (meaning the device has an accelerometer),
     * false otherwise
     */
    public boolean canDetectArmGestures() {
        return mLinearAccelerationSensor != null;
    }

    /**
     * Check is the device is able to detect wrist in/out gestures
     *
     * @return true if the device can detect arm gestures (meaning the device has a gyroscope),
     * false otherwise
     */
    public boolean canDetectWristGestures() {
        return mGyroscopeVectorSensor != null;
    }

    /**
     * Register the required SensorsEventlistener
     * This method should generally be called in onResume() of an Activity/Fragment
     */
    public void registerListener() {
        if ( canDetectArmGestures() ) {
            mSensorManager.registerListener( this, mLinearAccelerationSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
            );
            Log.d( TAG, "Accelerometer listener unregistered" );
        }

        if ( canDetectWristGestures() ) {
            mSensorManager.registerListener( this, mGyroscopeVectorSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
            );
            Log.d( TAG, "Gyroscope listener unregistered" );
        }
    }

    /**
     * Unregister the required SensorsEventlistener
     * This method should generally be called in onPause() of an Activity/Fragment
     */
    public void unregisterListener() {
        if ( canDetectWristGestures() ) {
            mSensorManager.unregisterListener( this, mGyroscopeVectorSensor );
            Log.d( TAG, "Gyroscope listener unregistered" );
        }
        if ( canDetectArmGestures() ) {
            mSensorManager.unregisterListener( this, mLinearAccelerationSensor );
            Log.d( TAG, "Accelerometer listener unregistered" );
        }
    }

    /**
     * Initialisation of the sensors.
     *
     * @param context a context to access the SensorManager (generally the application context)
     */
    private void intializeSensors( Context context ) {
        mSensorManager = ( SensorManager ) context.getSystemService( Context.SENSOR_SERVICE );
        initializeAccelerometer();
        initializeGyroscope();
    }

    /**
     * Initialisation of the accelerometer (linear)
     */
    private void initializeAccelerometer() {
        List< Sensor > sensors = mSensorManager.getSensorList( Sensor.TYPE_LINEAR_ACCELERATION );
        if ( sensors == null || sensors.isEmpty() ) {
            Log.e( TAG, "Accelerometer not available" );
        } else {
            mLinearAccelerationSensor = sensors.get( 0 );
        }
    }

    /**
     * Initialisation of the gyroscope
     */
    private void initializeGyroscope() {
        List< Sensor > rotationSensors = mSensorManager.getSensorList( Sensor.TYPE_GYROSCOPE );
        if ( rotationSensors == null || rotationSensors.isEmpty() ) {
            Log.e( TAG, "Gyroscope not available" );
        } else {
            mGyroscopeVectorSensor = rotationSensors.get( 0 );
        }
    }

    /**
     * Determine a SensorEvent should be throttltled because we detected a gesture recently
     */
    private boolean shouldDropEvent( SensorEvent event ) {
        return event.timestamp - mLastGestureDetectedTimestamp < THROTTLE_EVENT_TIME_MS * MS_TO_NS;
    }

    @Override
    public void onSensorChanged( SensorEvent event ) {
        if ( shouldDropEvent( event ) ) {
            return;
        }

        if ( event.sensor.equals( mLinearAccelerationSensor ) ) {
            onLinearAccelerationEvent( event.timestamp, event.values );
        } else if ( event.sensor.equals( mGyroscopeVectorSensor ) ) {
            onGyroscopeEvent( event.timestamp, event.values );
        }
    }

    @Override
    public void onAccuracyChanged( Sensor sensor, int accuracy ) {

    }

    /**
     * Handler for a gyroscope event
     *
     * @param timestamp the event timestamp in nano seconds
     * @param values    the values of the SensorEvent
     */
    private void onGyroscopeEvent( long timestamp, float[] values ) {
        if ( isGyroscopeEventSignificant( values ) ) {
            Log.d( TAG, String.format( "Timestamp : %d, values : (%f, %f, %f)",
                    timestamp, values[ AXIS_X ], values[ AXIS_Y ], values[ AXIS_Z ] )
            );
            onSignificantWristEvent( timestamp, values[ AXIS_X ] );
        }
    }

    /**
     * Check if the event is significant and we need to analyse it
     *
     * @param values the event values
     * @return true if the event should be handled, false otherwise
     */
    private boolean isGyroscopeEventSignificant( float[] values ) {
        return Math.abs( values[ 0 ] ) > SIGNIFICANT_THRESHOLD_GYROSCOPE;
    }

    /**
     * Handler for a significant arm event
     *
     * @param timestamp
     * @param value
     */
    private void onSignificantWristEvent( long timestamp, float value ) {
        if ( !shouldHandleSignificantWristGesture() ) {
            Log.w( TAG, "Dropping wrist event - arm gesture in progress" );
            return;
        }

        if ( !isWristGestureInProgress ) {
            startWristGestureDetection( value );
        } else {
            if ( value + mLastSignificantValue >= 0 ) {
                notifyListeners( GESTURE_WRIST_IN );
            } else if ( value + mLastSignificantValue < 0 ) {
                notifyListeners( GESTURE_WRIST_OUT );
            }
            finishWristGestureDetection( timestamp );
        }
    }

    /**
     * Start a wrist gesture detection (to prevent conflicting events)
     *
     * @param value the event value
     */
    private void startWristGestureDetection( float value ) {
        isWristGestureInProgress = true;
        mLastSignificantValue = value;
    }

    /**
     * Finish a wrist gesture detection (to prevent conflicting events)
     *
     * @param timestamp the event timestamp in nanoseconds
     */
    private void finishWristGestureDetection( long timestamp ) {
        mLastGestureDetectedTimestamp = timestamp;
        isWristGestureInProgress = false;
    }

    /**
     * Check if an arm gesture is in progress
     *
     * @return true if there is no other gesture in progress, false otherwise
     */
    private boolean shouldHandleSignificantWristGesture() {
        return !isArmGestureInProgress;
    }

    /**
     * Handler for a accelerometer event
     *
     * @param timestamp the event timestamp in nanoseconds
     * @param values    the values of the SensorEvent
     */
    private void onLinearAccelerationEvent( long timestamp, float[] values ) {
        if ( isEventSignificant( values ) ) {
            Log.i( TAG, String.format( "Timestamp : %d, values : (%f, %f, %f)",
                    timestamp, values[ AXIS_X ], values[ AXIS_Y ], values[ AXIS_Z ] )
            );
            onSignificantArmEvent( timestamp, values[ AXIS_Z ] );
        }
    }

    /**
     * Check if the event is significant and we need to analyse it
     *
     * @param values the event values
     * @return true if the event should be handled, false otherwise
     */
    private boolean isEventSignificant( float[] values ) {
        return Math.abs( values[ AXIS_Z ] ) > SIGNIFICANT_THRESHOLD_ACCELEROMETER;
    }

    /**
     * Handler for a significant arm event
     *
     * @param timestamp
     * @param value
     */
    private void onSignificantArmEvent( long timestamp, float value ) {
        if ( !shouldHandleSignificantArmGesture() ) {
            Log.w( TAG, "Dropping arm event - wrist gesture in progress" );
            return;
        }

        if ( !isArmGestureInProgress ) {
            startArmGestureDetection( value );
        } else {
            if ( value + mLastSignificantValue >= 0 ) {
                if ( mLastSignificantValue < 0 ) {
                    notifyListeners( GESTURE_ARM_DOWN );
                } else {
                    notifyListeners( GESTURE_ARM_UP );
                }
            } else if ( value + mLastSignificantValue < 0 ) {
                notifyListeners( GESTURE_ARM_DOWN );
            }
            finishArmGestureDetection( timestamp );
        }
    }

    /**
     * Start an arm gesture detection (to prevent conflicting events)
     *
     * @param value the event value
     */
    private void startArmGestureDetection( float value ) {
        isArmGestureInProgress = true;
        mLastSignificantValue = value;
    }

    /**
     * Finish an arm gesture detection
     *
     * @param timestamp the event timestamp in nanoseconds
     */
    private void finishArmGestureDetection( long timestamp ) {
        mLastGestureDetectedTimestamp = timestamp;
        isArmGestureInProgress = false;
    }

    /**
     * Check if a wrist gesture is in progress
     *
     * @return true if there is no other gesture in progress, false otherwise
     */
    private boolean shouldHandleSignificantArmGesture() {
        return !isWristGestureInProgress;
    }

    /**
     * Notify all listeners a wrist gesture has been detected
     */
    private void notifyListeners( int wristEvent ) {
        for ( int i = 0, size = mWristGestureListeners.size(); i < size; ++i ) {
            notifyListener( mWristGestureListeners.get( i ), wristEvent );
        }
    }

    /**
     * Notify a listener a wrist gesture has been detected
     *
     * @param listener     the listener to notify
     * @param wristGesture the gesture event
     */
    private void notifyListener( WristGestureListener listener, int wristGesture ) {
        switch ( wristGesture ) {
            case GESTURE_WRIST_OUT:
                listener.onWristOut();
                break;
            case GESTURE_WRIST_IN:
                listener.onWristIn();
                break;
            case GESTURE_ARM_DOWN:
                listener.onArmDown();
                break;
            case GESTURE_ARM_UP:
                listener.onArmUp();
                break;
        }
    }

}
