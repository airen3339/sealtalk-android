package io.rong.callkit;

import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

import io.rong.calllib.IRongReceivedCallListener;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallSession;
import io.rong.common.RLog;
import io.rong.imkit.RongIM;
import io.rong.imkit.manager.IExternalModule;
import io.rong.imkit.plugin.IPluginModule;
import io.rong.imlib.model.Conversation;

/**
 * Created by weiqinxiao on 16/8/15.
 */
public class RongCallModule implements IExternalModule {
    private final static String TAG = "RongCallModule";

    private RongCallSession mCallSession;
    private boolean mViewLoaded;
    private Context mContext;

    public RongCallModule() {
        RLog.i(TAG, "Constructor");
    }

    @Override
    public void onInitialized(String appKey) {
        RongIM.registerMessageTemplate(new CallEndMessageItemProvider());
    }

    @Override
    public void onConnected(String token) {
        RongCallClient.getInstance().setVoIPCallListener(RongCallProxy.getInstance());
    }

    @Override
    public void onCreate(final Context context) {
        mContext = context;
        IRongReceivedCallListener callListener = new IRongReceivedCallListener() {
            @Override
            public void onReceivedCall(final RongCallSession callSession) {
                RLog.d("VoIPReceiver", "onReceivedCall");
                if (mViewLoaded) {
                    startVoIPActivity(mContext, callSession, false);
                } else {
                    mCallSession = callSession;
                }
            }

            @Override
            public void onCheckPermission(RongCallSession callSession) {
                RLog.d("VoIPReceiver", "onCheckPermissions");
                if (mViewLoaded) {
                    startVoIPActivity(mContext, callSession, true);
                }
            }
        };

        RongCallClient.setReceivedCallListener(callListener);
    }

    /**
     * ?????????????????????????????? voip ??????????????????????????????????????????????????????
     * ???????????????????????????????????????????????????????????????voip ???????????????
     * <p>
     * ???????????????????????????????????????????????????????????????????????????
     * ??????????????????????????????????????????????????????????????????????????? mViewLoaded ??? onCreate ???????????? true ?????????
     */
    @Override
    public void onViewCreated() {
        mViewLoaded = true;
        if (mCallSession != null) {
            startVoIPActivity(mContext, mCallSession, false);
        }
    }

    @Override
    public List<IPluginModule> getPlugins(Conversation.ConversationType conversationType) {
        List<IPluginModule> pluginModules = new ArrayList<>();
        pluginModules.add(new AudioPlugin());
        pluginModules.add(new VideoPlugin());
        return pluginModules;
    }

    @Override
    public void onDisconnected() {

    }

    /**
     * ??????????????????
     *
     * @param context                  ?????????
     * @param callSession              ????????????
     * @param startForCheckPermissions android6.0?????????????????????????????????
     *                                 ???????????????????????????????????????startForCheckPermissions???true???
     *                                 ????????????????????????false???
     */
    private void startVoIPActivity(Context context, final RongCallSession callSession, boolean startForCheckPermissions) {
        RLog.d("VoIPReceiver", "startVoIPActivity");
        String action;
        if (callSession.getConversationType().equals(Conversation.ConversationType.DISCUSSION)
                || callSession.getConversationType().equals(Conversation.ConversationType.GROUP)) {
            if (callSession.getMediaType().equals(RongCallCommon.CallMediaType.VIDEO)) {
                action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIVIDEO;
            } else {
                action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIAUDIO;
            }
            Intent intent = new Intent(action);
            intent.putExtra("callSession", callSession);
            intent.putExtra("callAction", RongCallAction.ACTION_INCOMING_CALL.getName());
            if (startForCheckPermissions) {
                intent.putExtra("checkPermissions", true);
            } else {
                intent.putExtra("checkPermissions", false);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setPackage(context.getPackageName());
            context.startActivity(intent);
        } else {
            if (callSession.getMediaType().equals(RongCallCommon.CallMediaType.VIDEO)) {
                action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEVIDEO;
            } else {
                action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEAUDIO;
            }
            Intent intent = new Intent(action);
            intent.putExtra("callSession", callSession);
            intent.putExtra("callAction", RongCallAction.ACTION_INCOMING_CALL.getName());
            if (startForCheckPermissions) {
                intent.putExtra("checkPermissions", true);
            } else {
                intent.putExtra("checkPermissions", false);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setPackage(context.getPackageName());
            context.startActivity(intent);
        }
        mCallSession = null;
    }
}
