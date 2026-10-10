my current mod is related to make vilager reroll easier by placing a sign on workstation, write ench book name, right click on it
now players are too young to follow instructions and confused what to write and whats wrong in their world bcz they might be having otehr mods, wrong book name etc

to debug all I am planning to add ai based helper in game client 
so player can invoke local LLM which can pinpoint issue

add optional dependency code which connects llm only if its started on local
and then accepts command promots like reroll not working etc 
then llm will have access to in game logs to identify issue

1. add more logggers in my current mod so AI can trace issue if any
2. add integration for local llm and if llm not up just add replay in chat for prompt that llm is not running with some isntructions to download ollama and command to start llm 
3. use llm which can be free and can debug minecraft related code on local