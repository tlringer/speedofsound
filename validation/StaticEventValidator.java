import android.os.Parcelable;
import android.view.InputEvent;
import android.view.View;
import soot.*;
import soot.jimple.DefinitionStmt;
import soot.jimple.InvokeStmt;
import soot.options.Options;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * Static event validator for use with Soot
 *
 * First implementation: Dumbest one imaginable, just to get something working
 * Can make this fancier
 * TODO move somewhere else
 * TODO test all of this
 * TODO integrate with build process
 * TODO smarter treatment of library classes & dependencies
 * TODO whitelist vs. blacklist: Black nice for error-checking, but maybe if something is unknown we should error too
 *
 * For now:
 * javac -cp soot-trunk.jar:/home/tringer/android/platforms/android-23/android.jar ~/IdeaProjects/EvilApp/app/src/StaticEventValidator.java
 * java -ea -cp /home/tringer/IdeaProjects/EvilApp/app/src:/home/tringer/IdeaProjects/EvilApp/app/libs/soot-trunk.jar:/home/tringer/android/platforms/android-23/android.jar StaticEventValidator -process-dir ~/IdeaProjects/EvilApp/app/build/outputs/apk/app-debug.apk -p jtp.event-validation on -p cg.cha off -allow-phantom-refs -android-jars ~/android/platforms/ -x 'com.acg.*' -x 'com.google.*' -x 'com.android.*' -x 'android.support.*' -x 'sparta.*'  -f n
 * Add failOnError to fail on error
 */
public final class StaticEventValidator {

    // All click methods that are propagated downward from the ValidatedViewWrapper; when we expose new functionality in the wrapper, we need to add it here
    private static Set<String> CLICK_METHODS = new HashSet<>(asList(
            "performClick", "setPressed", "callOnClick", "performLongClick", "cancelLongPress", "onKeyDown", "onKeyUp",
            "onKeyMultiple", "setTouchDelegate"
    ));

    // Ways to create an event from a parcel
    private static Set<String> CREATE_FROM_PARCEL_METHODS = new HashSet<>(singletonList(
            "createFromParcel"
    ));

    // Ways to create an InputEvent
    private static Set<String> CREATE_INPUT_EVENT_METHODS = new HashSet<>(asList(
            "obtain", "createFromParcelBody"
    ));

    // Ways to modify an InputEvent
    private static Set<String> MODIFY_INPUT_EVENT_METHODS = new HashSet<>(asList(
            "setTainted", "recycle", "recycleIfNeededAfterDispatch", "cancel", "prepareForReuse", "setLocation",
            "offsetLocation", "setEdgeFlags", "setAction", "transform", "addBatch", "scale", "setSource",
            "setTargetAccessibilityFocus", "setDownTime", "setButtonState", "setActionButton", "startTracking"
    ));

    // Ways to copy an InputEvent
    private static Set<String> COPY_INPUT_EVENT_METHODS = new HashSet<>(asList(
            "copy", "obtainNoHistory", "clampNoHistory", "split", "changeTimeRepeat", "changeAction", "changeFlags"
    ));

    /**
     * Wrap Soot's main & run the static check
     */
    public static void main(String[] args) {

        // Optional argument to failOnError, which we don't want to pass to soot's main
        final boolean failOnError = "failOnError".equals(args[0]);
        if (failOnError) {
            args = Arrays.copyOfRange(args, 1, args.length);
        }

        Options.v().set_src_prec(Options.src_prec_apk);

        PackManager.v().getPack("jtp").add(new Transform("jtp.event-validation", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                boolean hasErrors = false;

                for(Unit u : b.getUnits()) {
                    if(u instanceof DefinitionStmt && ((DefinitionStmt) u).containsInvokeExpr()) {
                        SootMethod method = ((DefinitionStmt) u).getInvokeExpr().getMethod();
                        hasErrors = hasErrors || eventIsInvalid(method);
                    }
                    else if (u instanceof InvokeStmt) {
                        SootMethod method = ((InvokeStmt) u).getInvokeExpr().getMethod();
                        hasErrors = hasErrors || eventIsInvalid(method);
                    }
                }

                if (hasErrors && failOnError) {
                    throw new RuntimeException("Errors encountered: App uses programmatic clicks or creates/modifies/copies events");
                }
            }
        }));

        soot.Main.main(args);
    }

    /**
     * Check if an event is invalid
     */
    private static boolean eventIsInvalid(SootMethod method) {
        boolean isConstructor = method.isConstructor();
        String methodName = method.getName();
        SootClass clazz = method.getDeclaringClass();

        /**
         * Constructors for InputEvents also aren't allowed
         */
        if (isConstructor && isSubClass(clazz, InputEvent.class)) {
            printError("Constructing an event", methodName, clazz);
            return true;
        }

        return  eventIsInvalid(CLICK_METHODS, clazz, View.class, methodName, "Programmatic click") ||
                eventIsInvalid(CREATE_FROM_PARCEL_METHODS, clazz, Parcelable.Creator.class, methodName, "Creating an event from a parcel") ||
                eventIsInvalid(CREATE_INPUT_EVENT_METHODS, clazz, InputEvent.class, methodName, "Creating an event") ||
                eventIsInvalid(MODIFY_INPUT_EVENT_METHODS, clazz, InputEvent.class, methodName, "Modifying an event") ||
                eventIsInvalid(COPY_INPUT_EVENT_METHODS, clazz, InputEvent.class, methodName, "Copying an event");
    }

    /**
     * Check if an event is blacklisted and the class is a subclass of the superclass, and if so, print an error and return true
     */
    private static boolean eventIsInvalid(Set<String> blacklist, SootClass clazz, Class superClazz, String methodName, String error) {
        if (blacklist.contains(methodName) && isSubClass(clazz, superClazz)) {
            printError(error, methodName, clazz);
            return true;
        }

        return false;
    }

    /**
     * Check if a class is a subclass of the superclass
     */
    private static boolean isSubClass(SootClass clazz, Class superClazz) {
        String superClassName = superClazz.getName();

        if (clazz.getName().equals(superClassName)) { // TODO refactor
            return true;
        }

        while (clazz.hasSuperclass()) {
            clazz = clazz.getSuperclass();
            if (clazz.getName().equals(superClassName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Print an error
     */
    private static void printError(String error, String methodName, SootClass clazz) {
        System.out.println("ERROR: " + error);
        System.out.println("In method: " + methodName + ", class: " + clazz.getName());
    }
}
