package com.switkows.onkyoremote;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.GridView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.switkows.onkyoremote.communication.IscpCommands;
import com.switkows.onkyoremote.communication.IscpDeviceDiscover;
import com.switkows.onkyoremote.communication.ReceiverClient.CommandHandler;
import com.switkows.onkyoremote.communication.ReceiverClient.CommandSendCallbacks;
import com.switkows.onkyoremote.communication.ReceiverInfo;
import com.switkows.onkyoremote.dummy.DummyContent;

/**
 * A fragment representing a single Receiver detail screen.
 * This fragment is either contained in a {@link ReceiverListActivity} in two-pane mode (on tablets) or a
 * {@link ReceiverDetailActivity} on handsets.
 */
@SuppressLint("ValidFragment")
public class ReceiverDetailFragment extends Fragment implements CommandHandler {
   /**
    * The fragment argument representing the item ID that this fragment
    * represents.
    */
   public static final String     ARG_ITEM_ID = "item_id";
   public static final String     ARG_IP_ADDR = "ip_addr";
   public static final String     ARG_TCP_PORT= "tcp_port";

   private final static String[] BUTTON_LABELS = {"Power Off","Power On","Volume -", "Volume +", "Up", "Down",
                                                  "Left", "Right", "Enter", "Exit", "Menu"};
   private final int[] BUTTON_COMMANDS = {IscpCommands.POWER_OFF, IscpCommands.POWER_ON, IscpCommands.VOLUME_DOWN, IscpCommands.VOLUME_UP, IscpCommands.DIRECTION_UP, IscpCommands.DIRECTION_DOWN,
                                          IscpCommands.DIRECTION_LEFT, IscpCommands.DIRECTION_RIGHT, IscpCommands.BUTTON_ENTER, IscpCommands.BUTTON_EXIT, IscpCommands.BUTTON_MENU};

   //FIXME - split to different fragments (one for commands, one for console output
   //        but since this is a demo (i.e. non-final implementation), this is okay, maybe forever
   private boolean isCommandFragment;

   private ReceiverInfo mReceiverInfo;

   private CommandSendCallbacks mCommandSender;

   /**
    * The dummy content this fragment is presenting.
    */
   private DummyContent.DummyItem mItem;

   /**
    * Mandatory empty constructor for the fragment manager to instantiate the
    * fragment (e.g. upon screen orientation changes).
    */

   public ReceiverDetailFragment() {}

   @Override
   public void onAttach(Activity activity) {
      super.onAttach(activity);
      setReceiverInfo(((CommandHandler)activity).getReceiverInfo());
      mCommandSender = ((CommandSendCallbacks)activity);
   }

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      if(getArguments().containsKey(ARG_ITEM_ID)) {
         // Load the dummy content specified by the fragment
         // arguments. In a real-world scenario, use a Loader
         // to load content from a content provider.
         String id = getArguments().getString(ARG_ITEM_ID);
         mItem = DummyContent.ITEM_MAP.get(id);
         if(id.equals("1"))
            isCommandFragment = true;
         else
            isCommandFragment = false;
      }
      if(isCommandFragment) {
         setHasOptionsMenu(true);
      }
   }

   private Menu mMenu;//save pointer, so we can overwrite icons/text later
   @Override
   public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
//      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.main_menu, menu);
      mMenu = menu;
      super.onCreateOptionsMenu(menu, inflater);
//      Log.v("TJSDebug","called onCreateOptionsMenu");
      //restore state (i don't like doing it here, but i need to do it fairly late
      setReceiverInfo(mReceiverInfo);
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      switch(item.getItemId()) {
         case R.id.connectActionButton:
            toggleConnection();
            return true;
         case R.id.discoverButton:
            IscpDeviceDiscover columbus = new IscpDeviceDiscover();
            new AsyncTask<IscpDeviceDiscover,Void,IscpDeviceDiscover>() {
               @Override
               protected IscpDeviceDiscover doInBackground(IscpDeviceDiscover... params) {
                  params[0].discover(null,null);
                  return params[0];
               };
               @Override
               protected void onPostExecute(IscpDeviceDiscover result) {
                  Toast.makeText(getActivity(), result.printAllReceivers(), Toast.LENGTH_LONG).show();
               }
            }.execute(columbus);
            return true;
      }
      return super.onOptionsItemSelected(item);
   }

   @Override
   public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      View rootView;
//      Log.v("TJSDEBUG","called onCreateView");
      if(isCommandFragment) {
         rootView = inflater.inflate(R.layout.fragment_commands, container, false);

         //connect eventListeners to buttons
         View view;
         view = rootView.findViewById(R.id.inputSelector);
         view.setEnabled(false); //disable by default. once the connection to the server has been established and the value queried, the field will be enabled
         ((Spinner)view).setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
               //Note : only has an effect if the power is already on (this may not be desirable...
               //       I will later change to set the value of the Array to the current input
               //       value, on startup, so we don't turn on device upon app powerup)
               if(mCommandSender!=null)
                  mCommandSender.sendCommand(IscpCommands.SOURCE_DVR + arg2, false);
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
         });

         //attach listener to seekbar (volume slider) to update master volume
         view = rootView.findViewById(R.id.volumeInputBar);
         ((SeekBar)view).setOnSeekBarChangeListener(new VolumeListener(this));

         //attach listener to mute button
         view = rootView.findViewById(R.id.muteToggleButton);
         ((ToggleButton)view).setOnCheckedChangeListener(new MuteListener(this));

         //dynamically add buttons (saves copy/paste in layout file as well as attaching event handlers)
         GridView layout = (GridView)rootView.findViewById(R.id.buttons);
         layout.setAdapter(new ArrayAdapter<String>(rootView.getContext(), R.layout.simple_button_layout, R.id.simple_button, BUTTON_LABELS) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
               View retView = super.getView(position, convertView, parent);
               if(retView!=null) {
                  retView.setTag(getItemId(position));
                  //FIXME - only set once to a single instance of the listener?
                  //FIXME - add longClick for volume up/down/arrow buttons
                  retView.setOnClickListener(new OnClickListener() {
                     /***
                      * Define listener for button click events
                      */
                     @Override
                     public void onClick(View v) {
                        if(v.getTag() instanceof Long) {
                           long id = (Long)v.getTag();
                           if(id < BUTTON_COMMANDS.length && mCommandSender != null)
                              mCommandSender.sendCommand(BUTTON_COMMANDS[(int)id], true);
                        }
                     }
                  });
               }
               return retView;
            }
         });
      }
      else {
         rootView = inflater.inflate(R.layout.fragment_receiver_detail, container, false);

         // Show the dummy content as text in a TextView.
         if(mItem != null) {
            TextView view = (TextView)rootView.findViewById(R.id.receiver_detail);
            view.setText(mItem.content);
            view.setMovementMethod(new ScrollingMovementMethod());
         }
      }

      return rootView;
   }

   public void setReceiverInfo(ReceiverInfo info) {
      mReceiverInfo = info;
      if(mReceiverInfo != null) {
//       Log.v("TJSDEBUG", "onAttach called. setting up GUI state");
         onPowerChange(mReceiverInfo.isPoweredOn());
         onConnectionChange(mReceiverInfo.isConnected());
         onMuteChange(mReceiverInfo.isMuted());
         onVolumeChange(mReceiverInfo.getVolume());
         onInputChange(mReceiverInfo.getSource());
      }
   }
   @Override
   public void onMessageSent(String message) {
      // FIXME - add logging to other fragment?
   }

   private TextView mLogView;
   @Override
   public void onMessageReceived(String message, String response) {
      if(!isCommandFragment && this.isVisible()) {
         if(mLogView == null)
            mLogView = (TextView)getActivity().findViewById(R.id.receiver_detail);
         mLogView.setText(mLogView.getText() + "\n" + response);
      }
   }

   @Override
   public void onInputChange(int sourceVal) {
      if(getView()!=null) {
         Spinner view = (Spinner)this.getView().findViewById(R.id.inputSelector);
//         Log.v("TJS","Received Input: "+sourceVal);
         view.setSelection(sourceVal-IscpCommands.SOURCE_DVR);
      }
   }

   @Override
   public void onPowerChange(boolean powered_on) {}

   public void toggleConnection() {
      mCommandSender.toggleConnection();
   }

   @Override
   public void onConnectionChange(boolean isConnected) {
      //overwrite menu icon
      //FIXME - for some reason, on a one-pane configuration, this is triggered before mMenu is defined...
      if(mMenu != null) {
         MenuItem item = mMenu.findItem(R.id.connectActionButton);
//         Log.v("TJSDebug","Called onConnectionChange with menu = "+mMenu+", item = "+item+", connected:"+isConnected);
         if(item != null) {//FIXME - required to fix null pointer on context change
            if(isConnected) {
               item.setIcon(android.R.drawable.presence_online);
               item.setTitle("Disconnect from Server");
            } else {
               item.setIcon(android.R.drawable.stat_notify_sync_noanim);
               item.setTitle("Connect to Server");
            }
         }
      }
      if(getView()!=null) {
         View view = this.getView().findViewById(R.id.inputSelector);
         view.setEnabled(isConnected);
      }
   }

   @Override
   public void onMuteChange(boolean muted) {
      if(getView()!=null) {
         ToggleButton view = (ToggleButton)this.getView().findViewById(R.id.muteToggleButton);
         view.setChecked(muted);
      }
   }

   @Override
   public void onVolumeChange(float volume) {
      if(getView()!=null) {
         SeekBar view = (SeekBar)this.getView().findViewById(R.id.volumeInputBar);
         view.setProgress((int)volume);
      }
   }

   public void setVolume(float volume) {
      mCommandSender.setVolume(volume);
   }

   public void setMuted(boolean muted) {
      //set local state
      if(mReceiverInfo!=null)
         mReceiverInfo.setMuted(muted);
      //now send command to server
      if(muted)
         mCommandSender.sendCommand(IscpCommands.MUTE,true);
      else
         mCommandSender.sendCommand(IscpCommands.UNMUTE,true);
   }

   private class MuteListener implements OnCheckedChangeListener {
      private final ReceiverDetailFragment mParent;
      public MuteListener(ReceiverDetailFragment parent) {
         mParent = parent;
      }
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
         mParent.setMuted(isChecked);
      }
   }
   private class VolumeListener implements OnSeekBarChangeListener {
      private final ReceiverDetailFragment mParent;
      public VolumeListener(ReceiverDetailFragment parent) {
         mParent = parent;
      }
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
         if(fromUser) {
            mParent.setVolume((float)seekBar.getProgress());
         }

      }
      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
         mCommandSender.setVolumeTracked(true);
      }
      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
         mCommandSender.setVolumeTracked(false);
      }
   }
   
   @Override
   public ReceiverInfo getReceiverInfo() {
      // TODO Auto-generated method stub
      return null;
   }
}
