CS 6380.001
Project 1

To compile and run the program, follow these steps:
  1. Create a Project directory in the utdallas.edu filesystem which will have a /src and /bin subdirectory
  2. Upload the Process.java file under the src subdirectory
  3. Create a separate Launch directory
  4. Place the given launcher.sh, cleanup.sh, and the associated config.txt file to test in the Launch directory
  5. Modify the pathnames provided in the launcher.sh and cleanup.sh scripts as necessary
  6. Go into your Project directory, in the Process.java file, modify line 346 so that the filePath of the config.txt file reflects the path of your config file.
  7. Compile the Process.java file using the terminal command javac src/Process.java
  8. Move the compiled contents of the Process.java file into the /bin subdirectory. There should be the following .class files to move: Process.class, Process\$Message.class, Process\$MessageSender.class, Process\$MessageReceiver.class, and Process\&ConnectedClient.class
  9. To run the program, run ./launcher.sh within the Launch directory
  10. Wait for the program output. Once satisfied, cleanup the processes using ./cleanup.sh before launching the program again.
  
