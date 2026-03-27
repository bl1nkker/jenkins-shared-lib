import com.cloudbees.hudson.plugins.folder.Folder
import java.util.ConcurrentModificationException
import java.lang.IllegalStateException

def call(Map overrides = [:]) {
  Map config = [
    WHEN_I_MET_YOU_IN_SUMMER: params.WHEN_I_MET_YOU_IN_SUMMER
  ]
  echo "Params from parents: ${config.WHEN_I_MET_YOU_IN_SUMMER}"
}