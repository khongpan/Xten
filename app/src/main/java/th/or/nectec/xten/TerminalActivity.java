package th.or.nectec.xten;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.macroyau.blue2serial.BluetoothDeviceListDialog;
import com.macroyau.blue2serial.BluetoothSerial;
import com.macroyau.blue2serial.BluetoothSerialListener;
import th.or.nectec.xten.R;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.UnsupportedEncodingException;

public class TerminalActivity extends AppCompatActivity
        implements BluetoothSerialListener, BluetoothDeviceListDialog.OnDeviceSelectedListener {
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private BluetoothSerial bluetoothSerial;
    private ScrollView svTerminal;
    private TextView tvTerminal;
    private EditText etSend;
    private MenuItem actionConnect, actionDisconnect;
    private boolean crlf = false;
    private boolean RemoteDevice = false;
    private boolean MqttConnect = false;

    MqttAndroidClient client;

    String btRxBuff="";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);
        // Find UI views and set listeners
        svTerminal = (ScrollView) findViewById(R.id.terminal);
        tvTerminal = (TextView) findViewById(R.id.tv_terminal);
        etSend = (EditText) findViewById(R.id.et_send);
        etSend.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    String send = etSend.getText().toString().trim();
                    if (send.length() > 0) {
                        bluetoothSerial.write(send, crlf);
                        // etSend.setText("");
                        // mqttPubish(send);
                    }
                }
                return false;
            }
        });
        // Create a new instance of BluetoothSerial
        bluetoothSerial = new BluetoothSerial(this, this);
        //-----------------MQTT---------------------
        String clientId = MqttClient.generateClientId();
        MemoryPersistence persistence = new MemoryPersistence();
        //String broker = "tcp://m10.cloudmqtt.com:14991";
        //String broker = "tcp://iot.eclipse.org:1883";
        String broker = "tcp://broker.mqttdashboard.com:1883";
        client = new MqttAndroidClient(this.getApplicationContext(), broker, clientId, persistence);


    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check Bluetooth availability on the device and set up the Bluetooth adapter
        bluetoothSerial.setup();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Open a Bluetooth serial port and get ready to establish a connection
        if (bluetoothSerial.checkBluetooth() && bluetoothSerial.isBluetoothEnabled()) {
            if (!bluetoothSerial.isConnected()) {
                bluetoothSerial.start();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Disconnect from the remote device and close the serial port
        bluetoothSerial.stop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_terminal, menu);
        actionConnect = menu.findItem(R.id.action_connect);
        actionDisconnect = menu.findItem(R.id.action_disconnect);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_connect) {
            showDeviceListDialog();
            return true;
        } else if (id == R.id.action_disconnect) {
            bluetoothSerial.stop();
            return true;
        } else if (id == R.id.action_crlf) {
            crlf = !item.isChecked();
            item.setChecked(crlf);
            return true;
        } else if (id == R.id.RemoteDevice) {
            RemoteDevice = !item.isChecked();
            item.setChecked(RemoteDevice);
            return true;
        } else if (id == R.id.MqttConnect) {
            MqttConnect = !item.isChecked();
            item.setChecked(MqttConnect);
            if (MqttConnect == true) {
                //Toast.makeText(getApplicationContext(), "Test", Toast.LENGTH_SHORT).show();
                mqttConnect();
            } else {
                mqttDisconnect();
            }
            return true;
        } else if (id == R.id.MqttSubscribe) {
            mqttSubscribe();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void invalidateOptionsMenu() {
        if (bluetoothSerial == null)
            return;
        // Show or hide the "Connect" and "Disconnect" buttons on the app bar
        if (bluetoothSerial.isConnected()) {
            if (actionConnect != null)
                actionConnect.setVisible(false);

            if (actionDisconnect != null)
                actionDisconnect.setVisible(true);
        } else {
            if (actionConnect != null)
                actionConnect.setVisible(true);
            if (actionDisconnect != null)
                actionDisconnect.setVisible(false);

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ENABLE_BLUETOOTH:
                // Set up Bluetooth serial port when Bluetooth adapter is turned on
                if (resultCode == Activity.RESULT_OK) {
                    bluetoothSerial.setup();
                }
                break;
        }
    }

    private void updateBluetoothState() {
        // Get the current Bluetooth state
        final int state;
        if (bluetoothSerial != null)
            state = bluetoothSerial.getState();
        else
            state = BluetoothSerial.STATE_DISCONNECTED;

        // Display the current state on the app bar as the subtitle
        String subtitle;
        switch (state) {
            case BluetoothSerial.STATE_CONNECTING:
                subtitle = getString(R.string.status_connecting);
                break;
            case BluetoothSerial.STATE_CONNECTED:
                subtitle = getString(R.string.status_connected, bluetoothSerial.getConnectedDeviceName());
                break;
            default:
                subtitle = getString(R.string.status_disconnected);
                break;
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(subtitle);
        }
    }

    private void showDeviceListDialog() {
        // Display dialog for selecting a remote Bluetooth device
        BluetoothDeviceListDialog dialog = new BluetoothDeviceListDialog(this);
        dialog.setOnDeviceSelectedListener(this);
        dialog.setTitle(R.string.paired_devices);
        dialog.setDevices(bluetoothSerial.getPairedDevices());
        dialog.showAddress(true);
        dialog.useDarkTheme(true);
        dialog.show();
    }

    /* Implementation of BluetoothSerialListener */
    @Override
    public void onBluetoothNotSupported() {
        new MaterialDialog.Builder(this)
                .content(R.string.no_bluetooth)
                .positiveText(R.string.action_quit)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        super.onPositive(dialog);
                        finish();
                    }
                })
                .cancelable(false)
                .theme(Theme.DARK)
                .show();
    }

    @Override
    public void onBluetoothDisabled() {
        Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBluetooth, REQUEST_ENABLE_BLUETOOTH);
    }

    @Override
    public void onBluetoothDeviceDisconnected() {
        invalidateOptionsMenu();
        updateBluetoothState();
    }

    @Override
    public void onConnectingBluetoothDevice() {
        updateBluetoothState();
    }

    @Override
    public void onBluetoothDeviceConnected(String name, String address) {
        invalidateOptionsMenu();
        updateBluetoothState();
    }

    @Override
    public void onBluetoothSerialRead(String message) {
        // Print the incoming message on the terminal screen
        String sub_string[];

        if (message.indexOf('\n')>=0) {
            btRxBuff+=message;
            mqttPubish(btRxBuff);
            btRxBuff="";
        } else {
            btRxBuff+=message;
        }

        //tvTerminal.append(getString(R.string.terminal_message_template, bluetoothSerial.getConnectedDeviceName(), message));
        //tvTerminal.append(getString(R.string.terminal_message_template, "fromBt->", message));
        //svTerminal.post(scrollTerminalToBottom);
        /*
        if (message.indexOf('\n')>=0) {
            sub_string = message.split("\n");
            if (sub_string.length>0) {
                btRxBuff += sub_string[0];
            }
            btRxBuff += '\n';
            tvTerminal.append(getString(R.string.terminal_message_template, bluetoothSerial.getConnectedDeviceName(), btRxBuff));
            svTerminal.post(scrollTerminalToBottom);
            mqttPubish(btRxBuff);


            if(sub_string.length>1) {
                btRxBuff = sub_string[1];
            }else {
                btRxBuff = "";
            }
        } else {
            btRxBuff +=message;
        }
        */
    }

    @Override
    public void onBluetoothSerialWrite(String message) {
        // Print the outgoing message on the terminal screen
        //tvTerminal.append(getString(R.string.terminal_message_template, bluetoothSerial.getLocalAdapterName(), message));
        //tvTerminal.append(getString(R.string.terminal_message_template,"toBt->", message));
        //svTerminal.post(scrollTerminalToBottom);
    }

    @Override /* Implementation of BluetoothDeviceListDialog.OnDeviceSelectedListener */
    public void onBluetoothDeviceSelected(BluetoothDevice device) {
        // Connect to the selected remote Bluetooth device
        bluetoothSerial.connect(device);
    }/* End of the implementation of listeners */

    private final Runnable scrollTerminalToBottom = new Runnable() {
        @Override
        public void run() {
            svTerminal.fullScroll(ScrollView.FOCUS_DOWN);// Scroll the terminal screen to the bottom
        }
    };

    private void mqttConnect() {

        try {

            client.setCallback(new MqttCallback() {
                public void messageArrived(String topic, MqttMessage msg)
                        throws Exception {

                    String payload = new String(msg.getPayload());

                    System.out.println("Recived:" + topic);
                    System.out.println("Recived:" + new String(msg.getPayload()));
                    //System.out.println("Recived:" + topic);
                    //System.out.println("Recived:" + payload);
                    //tvTerminal.append(getString(R.string.terminal_message_template,"",payload));

                    //tvTerminal.append(payload);
                    tvTerminal.append(getString(R.string.terminal_message_template,"FromMqt->", payload));
                    svTerminal.post(scrollTerminalToBottom);

                    bluetoothSerial.write(payload, false);
                }

                public void deliveryComplete(IMqttDeliveryToken arg0) {
                    System.out.println("Delivary complete");
                }

                public void connectionLost(Throwable arg0) {
                    // TODO Auto-generated method stub
                }
            });

            MqttConnectOptions options = new MqttConnectOptions();
            String topic = "users/last/will";
            byte[] payload = "some payload".getBytes();
            options.setWill(topic, payload, 1, false);
            options.setCleanSession(true);
            options.setUserName("Test");
            options.setPassword("12345678".toCharArray());

            IMqttToken token = client.connect(options);

            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Toast toastConnection = Toast.makeText(getApplicationContext(), "Connection Success", Toast.LENGTH_LONG);
                    toastConnection.show();

                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Toast toastConnectionFailure = Toast.makeText(getApplicationContext(), "Connection Failure", Toast.LENGTH_LONG);
                    toastConnectionFailure.show();

                }

            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void mqttSubscribe() {
        String topic;
        if (RemoteDevice == true) {
            topic = "todevice";
        } else {
            topic = "fromdevice";
        }
        int qos = 0;
        try {
            final IMqttToken subToken = client.subscribe(topic, qos);
            subToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // The message was published
                    Toast toastsubscribeSuccess = Toast.makeText(getApplicationContext(), "Subscribe Success", Toast.LENGTH_LONG);
                    toastsubscribeSuccess.show();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken,
                                      Throwable exception) {
                    Toast toastsubscribeFailure = Toast.makeText(getApplicationContext(), "Subscribe Failure", Toast.LENGTH_LONG);
                    toastsubscribeFailure.show();
                    // The subscription could not be performed, maybe the user was not
                    // authorized to subscribe on the specified topic e.g. using wildcards

                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void mqttPubish(String TextMessage) {
        String topicpubish;
        if (RemoteDevice == true) {
            topicpubish = "fromdevice";

        } else {
            topicpubish = "todevice";
        }

        tvTerminal.append(getString(R.string.terminal_message_template, "toMq->" , TextMessage));
        svTerminal.post(scrollTerminalToBottom);

        //String topic = txtTopicText.getText().toString();
        String payload = TextMessage;
        byte[] encodedPayload = new byte[0];
        try {
            encodedPayload = payload.getBytes("UTF-8");
            MqttMessage message = new MqttMessage(encodedPayload);
            message.setQos(0);
            client.publish(topicpubish, message);
        } catch (UnsupportedEncodingException | MqttException e) {
            e.printStackTrace();
        }
    }

    private void mqttDisconnect() {
        try {
            IMqttToken disconToken = client.disconnect();
            disconToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // we are now successfully disconnected
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken,
                                      Throwable exception) {
                    // something went wrong, but probably we are disconnected anyway
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
