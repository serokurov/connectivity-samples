package com.google.location.nearby.apps.rockpaperscissors;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate.Status;
import com.google.android.gms.nearby.connection.Strategy;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/** Activity controlling the Rock Paper Scissors game */
public class MainActivity extends AppCompatActivity {

  private static final String TAG = "RockPaperScissors";

  private static final String[] REQUIRED_PERMISSIONS;
  static {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      REQUIRED_PERMISSIONS =
          new String[] {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
          };
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      REQUIRED_PERMISSIONS =
          new String[] {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
          };
    } else {
      REQUIRED_PERMISSIONS =
          new String[] {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
          };
    }
  }

  private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;

  private static final Strategy STRATEGY = Strategy.P2P_STAR;

  private static final int PICK_IMAGE = 1;

  // Our handle to Nearby Connections
  private ConnectionsClient connectionsClient;

  // Our randomly generated name
  private final String codeName = CodenameGenerator.generate();

  private String opponentEndpointId;
  private String opponentName;

  private Button findOpponentButton;
  private Button disconnectButton;
  private Button imageButton;

  private TextView opponentText;
  private TextView statusText;

  // Callbacks for receiving payloads
  private final PayloadCallback payloadCallback =
      new PayloadCallback() {
        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
          InputStream imageStream = payload.asStream().asInputStream();

          Bitmap imageBitmap = BitmapFactory.decodeStream(imageStream);

          ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
          imageBitmap.compress(Bitmap.CompressFormat.PNG, 50, imageBytes);
          String imagePath = MediaStore.Images.Media.insertImage(getContentResolver(),
                  imageBitmap, "Image", null);
          Uri imageUri =  Uri.parse(imagePath);

          Intent viewerIntent = new Intent();
          viewerIntent.setAction(android.content.Intent.ACTION_VIEW);
          viewerIntent.setDataAndType(imageUri, "image/png");
          startActivity(viewerIntent);
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
          Log.i(TAG, "onPayloadTransferUpdate: update status=" + update);
        }
      };

  // Callbacks for finding other devices
  private final EndpointDiscoveryCallback endpointDiscoveryCallback =
      new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
          Log.i(TAG, "onEndpointFound: endpoint found, connecting");
          connectionsClient.requestConnection(codeName, endpointId, connectionLifecycleCallback);
        }

        @Override
        public void onEndpointLost(String endpointId) {}
      };

  // Callbacks for connections to other devices
  private final ConnectionLifecycleCallback connectionLifecycleCallback =
      new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
          Log.i(TAG, "onConnectionInitiated: accepting connection");
          connectionsClient.acceptConnection(endpointId, payloadCallback);
          opponentName = connectionInfo.getEndpointName();
        }

        @Override
        public void onConnectionResult(String endpointId, ConnectionResolution result) {
          if (result.getStatus().isSuccess()) {
            Log.i(TAG, "onConnectionResult: connection successful");

            connectionsClient.stopDiscovery();
            connectionsClient.stopAdvertising();

            opponentEndpointId = endpointId;
            setOpponentName(opponentName);
            setStatusText(getString(R.string.status_connected));
            setButtonState(true);
          } else {
            Log.i(TAG, "onConnectionResult: connection failed");
          }
        }

        @Override
        public void onDisconnected(String endpointId) {
          Log.i(TAG, "onDisconnected: disconnected from the opponent");
          resetGame();
        }
      };

  @Override
  protected void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.activity_main);

    findOpponentButton = findViewById(R.id.find_opponent);
    disconnectButton = findViewById(R.id.disconnect);
    imageButton = findViewById(R.id.image);

    opponentText = findViewById(R.id.opponent_name);
    statusText = findViewById(R.id.status);

    TextView nameView = findViewById(R.id.name);
    nameView.setText(getString(R.string.codename, codeName));

    connectionsClient = Nearby.getConnectionsClient(this);

    resetGame();
  }

  @Override
  protected void onStart() {
    super.onStart();

    if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
      requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS);
    }
  }

  @Override
  protected void onStop() {
    // This callback is called when the image picker activity opens on top of us,
    // so we can't stop the endpoint here
    //connectionsClient.stopAllEndpoints();
    //resetGame();

    super.onStop();
  }

  /** Returns true if the app was granted all the permissions. Otherwise, returns false. */
  private static boolean hasPermissions(Context context, String... permissions) {
    for (String permission : permissions) {
      if (ContextCompat.checkSelfPermission(context, permission)
          != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  /** Handles user acceptance (or denial) of our permission request. */
  @CallSuper
  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode != REQUEST_CODE_REQUIRED_PERMISSIONS) {
      return;
    }

    int i = 0;
    for (int grantResult : grantResults) {
      if (grantResult == PackageManager.PERMISSION_DENIED) {
        Log.i(TAG, "Failed to request the permission " + permissions[i]);
        Toast.makeText(this, R.string.error_missing_permissions, Toast.LENGTH_LONG).show();
        finish();
        return;
      }
      i++;
    }
    recreate();
  }

  /** Finds an opponent to play the game with using Nearby Connections. */
  public void findOpponent(View view) {
    startAdvertising();
    startDiscovery();
    setStatusText(getString(R.string.status_searching));
    findOpponentButton.setEnabled(false);
  }

  /** Disconnects from the opponent and reset the UI. */
  public void disconnect(View view) {
    connectionsClient.disconnectFromEndpoint(opponentEndpointId);
    resetGame();
  }

  /** Starts looking for other players using Nearby Connections. */
  private void startDiscovery() {
    // Note: Discovery may fail. To keep this demo simple, we don't handle failures.
    connectionsClient.startDiscovery(
            getPackageName(), endpointDiscoveryCallback,
            new DiscoveryOptions.Builder().setStrategy(STRATEGY).build());
  }

  /** Sends image to the other player. */
  public void makeMove(View view) {
    if (view.getId() == R.id.image) {
      pickImage();
    }
  }

  /** Broadcasts our presence using Nearby Connections so other players can find us. */
  private void startAdvertising() {
    // Note: Advertising may fail. To keep this demo simple, we don't handle failures.
    connectionsClient.startAdvertising(
            codeName, getPackageName(), connectionLifecycleCallback,
            new AdvertisingOptions.Builder().setStrategy(STRATEGY).build());
  }

  /** Wipes all game state and updates the UI accordingly. */
  private void resetGame() {
    opponentEndpointId = null;
    opponentName = null;
    setOpponentName(getString(R.string.no_opponent));
    setStatusText(getString(R.string.status_disconnected));
    setButtonState(false);
  }

  /** Enables/disables buttons depending on the connection status. */
  private void setButtonState(boolean connected) {
    findOpponentButton.setEnabled(true);
    findOpponentButton.setVisibility(connected ? View.GONE : View.VISIBLE);
    disconnectButton.setVisibility(connected ? View.VISIBLE : View.GONE);

    setGameChoicesEnabled(connected);
  }

  /** Enables/disables the rock, paper, and scissors buttons. */
  private void setGameChoicesEnabled(boolean enabled) {
    imageButton.setEnabled(enabled);
  }

  /** Shows a status message to the user. */
  private void setStatusText(String text) {
    statusText.setText(text);
  }

  /** Updates the opponent name on the UI. */
  private void setOpponentName(String opponentName) {
    opponentText.setText(getString(R.string.opponent_name, opponentName));
  }

  private void pickImage() {
    Intent pickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
    pickerIntent.setType("image/*");
    startActivityForResult(pickerIntent, PICK_IMAGE);
  }

  /** Processes a started activity result */
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {
      if (data == null) {
        //TODO - Display an error
        return;
      }
      try {
        InputStream imageStream = this.getContentResolver().openInputStream(data.getData());
        Payload imagePayload = Payload.fromStream(imageStream);

        connectionsClient.sendPayload(opponentEndpointId, imagePayload);

      } catch (FileNotFoundException e) {
        e.printStackTrace();
      }
    }
  }
}
