# Session Context

## User Prompts

### Prompt 1

I'm developing a plugin for IntelliJ IDE. I'm trying to implement listeners for editor events like moving the carrot or changing the selection. I've added listeners in the plugin.xml @src/main/resources/META-INF/plugin.xml And you can check the corresponding code implementation. Right now it's super basic, it just prints the event to the output. For some reason it doesn't work. I can only see event for file opened, but for carry position and selection position listener, I don't see anything in t...

### Prompt 2

Yes, it works now. I have another problem, I have a lot of log messages using info level and I cannot see them. Looks like somehow I need to change the log level.

### Prompt 3

Okay, I'm trying to expose the current position. We are rest service looks like it's not working. Can you please investigate? I have @src/main/kotlin/com/github/sintezcs/jetcodesync/http/FilePositionRestService.kt

### Prompt 4

NOw the endpoint returns 404 http://localhost:63342/api/filePosition

### Prompt 5

How can I find the sandbox port programmatically? I will need  to show the current port in the plugin tool window. Please update the existing tool window and add this information there. make sure the tllo window is registered correctly in the plugin.

### Prompt 6

I also want to track the event when I switch between tabs in the editor, so it will mean that I'm switching to another open file. So the REST API should reflect this when I just switched it up, because now it shows the information about the old file and it only updates when I move the caret.

### Prompt 7

Now we need to implement the reverse synchronization. The VS Code IDE exposes the same API endpoint running on a different port that returns the same JSON schema for the current open file and position. We need to pull this endpoint and open the file or move the cursor depending on the payload received. The logic should be the following. We only need to start polling when the IDE window loses its focus. So by default we are not polling. Once the window loses the focus we should start polling. The...

### Prompt 8

I can see that the status is polling, but the changes are not being applied, the cursor is not moving, the files are not switching.

### Prompt 9

we need to redesign the plugin panel right now it looks like a a really technical POC version. First of all we need to show it on the left not at the bottom.

### Prompt 10

Okay, I'd like to make it more user friendly in terms of choosing the sync destination and I wanted to auto-detect the possible IDEs to sync with. I think we need to define a list of say 20 different ports that the extension can use. On start the extension should scan all of the ports to find which of them are open and and query the new status endpoint. The status endpoint will return the ID name and the currently open project name.The plugin should store the list of detected ports and correspon...

### Prompt 11

How can I build and pack the plugin to install it locally into my JetBrains IDs?

### Prompt 12

run it

### Prompt 13

Enable sync checkbox in the plugin panel, does not work. If I uncheck it, the plugin still continues to sync.

### Prompt 14

I need you to write a comprehensive readme for the plugin repository and also please update the metadata for the plugin so plugin description will be also correct when looking at it in the IDE settings.

### Prompt 15

[Request interrupted by user for tool use]

### Prompt 16

Now please write a similar README file for the VS Code version in the vscode/jetcodesync dir

### Prompt 17

commit all changes

### Prompt 18

[Request interrupted by user]

### Prompt 19

Continue from where you left off.

### Prompt 20

commit all changes

