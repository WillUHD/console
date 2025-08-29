<div align="center">
  
Maradona Console
=
A lightweight, high performance console built using Swing. 
<div align="left">

- beats the UNIX terminal by around 10X, using tricks such as batching and blitting
- custom doc models for memory efficiency in exchange of versatility
- fast is its only advantage, and it kind of sucks at everything else
- it does have a smart input mechanism but it's busy-wait and not guaranteed to be reliable with concurrent input requests
- you need to change images from the predefined images/ directory, hack if different

### Deprecated! 

⚠️ Since Maradona's switch to the default UNIX terminal, this experiment has been deprecated. However, it's still quite an interesting piece of code!

- I decided to switch away from this custom written terminal because it doesn't match the level of quality I'd expect in "production" software, also I don't really need it.
- Please do not use this, it is garbage. Unless you somehow need a Java UI terminal for exclusively printing UTF8 text at a rate of 2 million lines/s. 
- Ask yourself this question: Do I need a Java UI terminal to output data at a rate of 2 million lines per second?
- Put on GitHub to at least archive the code I spent weeks on before I delete it

### Current limitations (Not fixing them)

- Cannot output colored text (as you can see, errors use the ❌ emoji)
- Doesn't have H scrollbar or linewrap (actually used to but lazy to implement)
- Can't be instantiated
- Fixed title is drawn through images

> a Maradona experiment by WillUHD, 2025
>
> peace 
