package jetbrains.buildServer.clouds.vmware;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.vmware.vim25.LocalizedMethodFault;
import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.vmware.connector.VMWareApiConnector;
import jetbrains.buildServer.clouds.vmware.tasks.TaskStatusUpdater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 4/15/2014
 *         Time: 3:58 PM
 */
public class VMWareCloudImage implements CloudImage {

  private static final Logger LOG = Logger.getInstance(VMWareCloudImage.class.getName());

  private final String myImageName;
  private final Map<String, VMWareCloudInstance> myInstances;
  private final VMWareImageType myImageType;
  private final VMWareImageStartType myStartType;
  @Nullable private final String mySnapshotName;
  private CloudErrorInfo myErrorInfo;
  private final String myResourcePool;
  private final String myFolder;
  private final VMWareApiConnector myApiConnector;
  private final int myMaxInstances;
  @NotNull private final TaskStatusUpdater myStatusTask;

  public VMWareCloudImage(@NotNull final VMWareApiConnector apiConnector,
                          @NotNull final String imageName,
                          @NotNull final VMWareImageType imageType,
                          @NotNull final String folder,
                          @NotNull final String resourcePool,
                          @Nullable final String snapshotName,
                          @Nullable final InstanceStatus imageInstanceStatus,
                          @NotNull final TaskStatusUpdater statusTask,
                          @NotNull final VMWareImageStartType startType,
                          final int maxInstances) {

    myImageName = imageName;
    myImageType = imageType;
    myFolder = folder;
    myResourcePool = resourcePool;
    mySnapshotName = snapshotName;
    myApiConnector = apiConnector;
    myStatusTask = statusTask;
    myMaxInstances = maxInstances;
    if (myImageType == VMWareImageType.TEMPLATE && startType == VMWareImageStartType.START) {
      myStartType = VMWareImageStartType.CLONE;
    } else {
      myStartType = startType;
    }
    if (myStartType == VMWareImageStartType.START) {
      final VMWareCloudInstance imageInstance = new VMWareCloudInstance(this, imageName, snapshotName);
      imageInstance.setStatus(imageInstanceStatus);
      myInstances = Collections.singletonMap(imageName, imageInstance);
    } else {
      myInstances = new HashMap<String, VMWareCloudInstance>();
    }
  }

  @NotNull
  public String getId() {
    return myImageName;
  }

  @NotNull
  public String getName() {
    return myImageName;
  }

  public VMWareImageType getImageType() {
    return myImageType;
  }

  @Nullable
  public String getSnapshotName() {
    return mySnapshotName;
  }

  @NotNull
  public Collection<VMWareCloudInstance> getInstances() {
    return myInstances.values();
  }

  @Nullable
  public CloudInstance findInstanceById(@NotNull String instanceId) {
    return myInstances.get(instanceId);
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorInfo;
  }

  public void setErrorInfo(final CloudErrorInfo errorInfo) {
    myErrorInfo = errorInfo;
  }

  private  synchronized VMWareCloudInstance getOrCreateInstance() throws RemoteException, InterruptedException {
    if (myStartType.isUseOriginal()) {
      LOG.info("Won't create a new instance - using original");
      return new VMWareCloudInstance(this, myImageName, null);
    }

    String latestSnapshotName = null;
    if (!myStartType.isDeleteAfterStop()) {
      // on demand clone
      final Map<String, VirtualMachine> clones = myApiConnector.getClones(myImageName);

      if (mySnapshotName != null) { ////means latest clone of snapshot that fits a mask
        latestSnapshotName = myApiConnector.getLatestSnapshot(myImageName, mySnapshotName);
        if (latestSnapshotName == null) {
          setErrorInfo(VMWareCloudErrorInfoFactory.noSuchSnapshot(mySnapshotName, myImageName));
          throw new IllegalArgumentException("Unable to find snapshot: " + mySnapshotName);
        }
      }
      // start an existsing one.
      final VirtualMachine imageVM = myApiConnector.getInstanceDetails(myImageName);
      if (imageVM == null) {
        throw new IllegalArgumentException("Unable to get VM details: " + mySnapshotName);
      }
      final VirtualMachineConfigInfo config = imageVM.getConfig();
      for (VirtualMachine vm : clones.values()) {
        if (myApiConnector.getInstanceStatus(vm) == InstanceStatus.STOPPED) {
          final Map<String, String> teamcityParams = myApiConnector.getTeamcityParams(vm);
          if (latestSnapshotName == null) {
            if (!config.getChangeVersion().equals(vm.getConfig().getChangeVersion())) {
              LOG.info(String.format("Change version for %s is outdated: '%s' vs '%s'", vm.getName(), vm.getConfig().getChangeVersion(), config.getChangeVersion()));
              deleteInstance(myInstances.get(vm.getName()));
              continue;
            }
          } else {
            if (!latestSnapshotName.equals(teamcityParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT))) {
              LOG.info(String.format("VM %s Snapshot is not the latest one: '%s' vs '%s'",
                                     vm.getName(),
                                     teamcityParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT),
                                     latestSnapshotName));
              deleteInstance(myInstances.get(vm.getName()));
              continue;
            }
          }
          LOG.info("Will use existing VM with name " + vm.getName());
          return new VMWareCloudInstance(this, vm.getName(), latestSnapshotName);
        }
      }
    }
    // wasn't able to find an existing candidate, so will clone into a new VM
    final String newVmName = generateNewVmName();
    LOG.info("Will create a new VM with name " + newVmName);
    return new VMWareCloudInstance(this, newVmName, latestSnapshotName);
  }

  public synchronized VMWareCloudInstance startInstance(@NotNull final CloudInstanceUserData cloudInstanceUserData) {
    try {
      final VMWareCloudInstance instance = getOrCreateInstance();
      boolean willClone = !myApiConnector.checkVirtualMachineExists(instance.getName());
      LOG.info("Will clone for " + instance.getName() + ": " + willClone);
      instance.setStatus(InstanceStatus.SCHEDULED_TO_START);
      if (willClone) {
        final Task task = myApiConnector.cloneVm(instance, myResourcePool, myFolder);
        myStatusTask.submit(task, new ImageStatusTaskWrapper(instance) {
          @Override
          public void onSuccess() {
            cloneVmSuccessHandler(instance, cloudInstanceUserData);
          }
        });
      } else {
        cloneVmSuccessHandler(instance, cloudInstanceUserData);
      }
      return instance;
    } catch (RemoteException e) {
      return null;
    } catch (InterruptedException e) {
      return null;
    }
  }

  private synchronized void cloneVmSuccessHandler(@NotNull final VMWareCloudInstance instance, @NotNull final CloudInstanceUserData cloudInstanceUserData) {
    if (!myInstances.containsKey(instance.getName())) {
      addInstance(instance);
    }
    instance.setStatus(InstanceStatus.STARTING);
    try {
      final Task startInstanceTask = myApiConnector.startInstance(instance,
                                                                  instance.getName(),
                                                                  cloudInstanceUserData);

      myStatusTask.submit(startInstanceTask, new ImageStatusTaskWrapper(instance) {
        @Override
        public void onSuccess() {
          reconfigureVmTask(instance, cloudInstanceUserData);
        }
      });
    } catch (RemoteException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private synchronized void reconfigureVmTask(@NotNull final VMWareCloudInstance instance, @NotNull final CloudInstanceUserData cloudInstanceUserData) {
    final Task task;
    try {
      task = myApiConnector.reconfigureInstance(instance, instance.getName(), cloudInstanceUserData);
      myStatusTask.submit(task, new ImageStatusTaskWrapper(instance) {
        @Override
        public void onSuccess() {
          instance.setStatus(InstanceStatus.RUNNING);
          LOG.info("Instance started successfully");
        }
      });
    } catch (RemoteException e) {
      e.printStackTrace();
    }
  }

  public void updateRunningInstances(final ProcessImageInstancesTask task) {
    for (VMWareCloudInstance instance : myInstances.values()) {
      task.processInstance(instance);
    }
  }

  public void populateInstances(final Map<String, VirtualMachine> currentInstances) {
    final List<String> instances2add = new ArrayList<String>();
    final List<String> instances2remove = new ArrayList<String>();

    for (String name : myInstances.keySet()) {
      if (currentInstances.get(name) == null) {
        instances2remove.add(name);
      }
    }

    for (String name : currentInstances.keySet()) {
      if (myInstances.get(name) == null) {
        instances2add.add(name);
      }
    }
    for (String name : instances2remove) {
      removeInstance(name);
    }
    for (String name : instances2add) {
      final VirtualMachine vm = currentInstances.get(name);
      final Map<String, String> teamcityParams = myApiConnector.getTeamcityParams(vm);
      final String snapshotName = teamcityParams.get(VMWareApiConnector.TEAMCITY_VMWARE_IMAGE_SNAPSHOT);

      final VMWareCloudInstance instance = new VMWareCloudInstance(this, name, StringUtil.isEmpty(snapshotName) ? null : snapshotName);
      instance.setStatus(myApiConnector.getInstanceStatus(vm));
      addInstance(instance);
    }
  }

  private static boolean isPermanent(InstanceStatus status){
    switch (status){
      case RUNNING:
      case STOPPED:
        return true;
      default:
        return false;
    }
  }


  public void stopInstance(@NotNull final VMWareCloudInstance instance) throws RemoteException, InterruptedException {
    LOG.info("Stopping instance " + instance.getName());
    myApiConnector.stopInstance(instance);
    instance.setStatus(InstanceStatus.STOPPED);
    if (myStartType.isDeleteAfterStop()) { // we only destroy proper instances.
      deleteInstance(instance);
    }
  }

  private void deleteInstance(@NotNull final VMWareCloudInstance instance) throws RemoteException, InterruptedException {
    if (instance.getErrorInfo() == null) {
      LOG.info("Will delete instance " + instance.getName());
      final Task task = myApiConnector.deleteVirtualMachine(myApiConnector.getInstanceDetails(instance.getName()));
      myStatusTask.submit(task, new TaskStatusUpdater.TaskCallbackAdapter());
      removeInstance(instance.getName());
    } else {
      LOG.warn(String.format("Won't delete instance %s with error: %s (%s)",
                             instance.getName(), instance.getErrorInfo().getMessage(), instance.getErrorInfo().getDetailedMessage()));
    }
  }

  public boolean canStartNewInstance() throws RemoteException {
    if (getImageType() == VMWareImageType.INSTANCE && myStartType == VMWareImageStartType.START) {
      return myApiConnector.isInstanceStopped(getId());
    }
    int runningInstancesCount = 0;
    for (Map.Entry<String, VMWareCloudInstance> entry : myInstances.entrySet()) {
      if (entry.getValue().getStatus() != InstanceStatus.STOPPED)
        runningInstancesCount++;
    }
    return myErrorInfo == null && (myMaxInstances == 0 || runningInstancesCount < myMaxInstances);
  }

  public VMWareImageStartType getStartType() {
    return myStartType;
  }

  public String getResourcePool() {
    return myResourcePool;
  }

  public String getFolder() {
    return myFolder;
  }

  private String generateNewVmName() {
    Random r = new Random();
    SimpleDateFormat sdf = new SimpleDateFormat("MMdd-hhmmss");
    return String.format("%s-clone-%s%s", getId(), sdf.format(new Date()), Integer.toHexString(r.nextInt(256)));
  }

  private void addInstance(@NotNull final VMWareCloudInstance instance){
    myInstances.put(instance.getName(), instance);
  }

  private void removeInstance(@NotNull final String name){
    myInstances.remove(name);
  }

  public static interface ProcessImageInstancesTask{
    void processInstance(@NotNull final VMWareCloudInstance instance);
  }

  private static class ImageStatusTaskWrapper extends TaskStatusUpdater.TaskCallbackAdapter {


    @NotNull protected final VMWareCloudInstance myInstance;

    public ImageStatusTaskWrapper(@NotNull final VMWareCloudInstance instance) {
      myInstance = instance;
    }

    @Override
    public void onError(final LocalizedMethodFault fault) {
      myInstance.setStatus(InstanceStatus.ERROR);
      final CloudErrorInfo errorInfo;
      if (fault.getFault() != null && fault.getFault().getCause() != null) {
        errorInfo = new CloudErrorInfo(fault.getLocalizedMessage(), fault.getLocalizedMessage(), fault.getFault().getCause());
      } else {
        errorInfo = new CloudErrorInfo(fault.getLocalizedMessage(), fault.getLocalizedMessage());
      }
      myInstance.setErrorInfo(errorInfo);
      LOG.info("Unknown error occured: " + fault.getLocalizedMessage());
    }
  }
}
