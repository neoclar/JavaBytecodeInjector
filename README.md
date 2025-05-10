# JavaBytecodeInjector
A tool for injecting your code into someone else's without changing the original files. Uses the ASM library and requires it to work.

# Start
Download <a href="https://github.com/neoclar/JavaBytecodeInjector/releases">latest version from releases page</a>

# Creating your modification
For example, we want to change the file <strong>com\authorOfOriginal\nameOfOriginalProject\Launcher</strong>
For everything to work, you need to create the "source" folder, and in it our file with changes in our "source" folder along the path of source filer: <strong>com\yourNick\nameOfYourModification\source\com\authorOfOriginal\nameOfOriginalProject\Launcher</strong>

Now, let's repeat the structure of the original file, because otherwise it simply won't compile.

In the method you want to change, to mark your changes, you need to use static methods from the <strong>com.neoclar.jbi.java.Marker</strong> class:
<ul dir="auto">
<li><strong>beforeMarker()</strong> - start of code insertion before the method, requires <strong>stopMarker</strong>.</li>
<li><strong>afterMarker()</strong> - Undesirable. Start of code insertion after execution of the main body of the method, requires <strong>stopMarker</strong>. May not work if you do not remove return in the original method.</li>
<li><strong>lineMarker(int line)</strong> - start of code insertion into the method after the line specified in the argument, requires <strong>stopMarker</strong>.</li>
<li><strong>stopMarker()</strong> - end of code insertion into the method. It is needed after <strong>beforeMarker</strong>, <strong>afterMarker</strong> and <strong>lineMarker</strong>.</li>
<li><strong>beforeOneMarker</strong>, <strong>afterOneMarker</strong>, <strong>lineOneMarker</strong> — Inserts your code in the same places as the versions without One, but does not require <strong>stopMarker</strong> because it only inserts one line. Unintuitive usage — insert return at the end of the method, simply because the compiler rejects any code after return.</li>
<li><strong>deleteMarker(int line)</strong> — deletes the line of code in the original method.</li>
<li><strong>deleteMarker (int lineStart, int lineEnd)</strong> — deletes the lines of code in the original method from lineStart inclusive to lineEnd inclusive.</li>
<li><strong>variableMarker(int varID)</strong> — specifies that this variable is in the original method. The argument requires the ordinal number of this variable (the name cannot be used even in an ideal outcome, because the compiler replaces names with numbers).</li>
<li><strong>GoToMarker(int line)</strong> — implementation of continue, break. Requires the line number to go to.</li>
<li><strong>deleteMethodMarker()</strong> — Deprecated for compatibility with other modifications. Deletes the method, it will not appear in the loaded class.</li>
<li><strong>anonymusClassMarker(int classID, Object classObject)</strong> — if you want to change something in an anonymous class — a class that is created directly in a method. <strong>classID</strong> — Id after $ in the name after compiling the outer class. <strong>classObject</strong> — create an instance of your anonymous class in this argument.</li>
<li><strong>plug()</strong> — a stub if everything breaks because of Frame. Can be useful after conditions or loops.</li>
</ul>
