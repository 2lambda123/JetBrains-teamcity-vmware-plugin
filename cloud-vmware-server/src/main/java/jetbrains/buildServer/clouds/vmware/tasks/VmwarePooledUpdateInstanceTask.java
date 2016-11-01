package jetbrains.buildServer.clouds.vmware.tasks;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.clouds.vmware.VMWareCloudClient;
import jetbrains.buildServer.clouds.vmware.VmwareCloudImage;
import jetbrains.buildServer.clouds.vmware.VmwareCloudInstance;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import org.jetbrains.annotations.NotNull;

/**
 * Created by sergeypak on 26/10/2016.
 */
public class VmwarePooledUpdateInstanceTask
  extends UpdateInstancesTask<VmwareCloudInstance, VmwareCloudImage, VMWareCloudClient> {
  private static final Logger LOG = Logger.getInstance(VmwarePooledUpdateInstanceTask.class.getName());

  private final List<VMWareCloudClient> myClients = new CopyOnWriteArrayList<>();
  private final PooledTaskObsoleteHandler myHandler;


  public VmwarePooledUpdateInstanceTask(@NotNull final VMWareApiConnector connector,
                                        @NotNull final VMWareCloudClient client,
                                        @NotNull final PooledTaskObsoleteHandler obsoleteHandler) {
    super(connector, client);
    myHandler = obsoleteHandler;
  }

  public void run(){
    super.run();
  }

  /**
   * Only run if the client is the top one in the list
   */
  public void runIfNecessary(@NotNull final VMWareCloudClient client){
    if (myClients.size() == 0){
      return;
    }
    if (client != myClients.get(0)){
      return;
    }
    run();
  }

  @NotNull
  @Override
  protected Collection<VmwareCloudImage> getImages() {
    return myClients.stream()
                    .flatMap(client->client.getImages().stream())
                    .collect(Collectors.toList());
  }

  public void addClient(@NotNull VMWareCloudClient client){
    synchronized (this) {
      myClients.add(client);
    }
  }

  public void removeClient(@NotNull VMWareCloudClient client){
    synchronized (this) {
      myClients.remove(client);
      if (myClients.isEmpty()){
        myHandler.pooledTaskObsolete(this);
      }
    }
  }

  public String getKey(){
    return myConnector.getKey();
  }

  public interface PooledTaskObsoleteHandler {
    public void pooledTaskObsolete(@NotNull VmwarePooledUpdateInstanceTask task);
  }
}