<div align="center">
  
Console
=
A lightweight, high performance console built using Swing. 
<div align="left">

- beats the UNIX terminal by around 10X, using tricks such as batching and blitting
- custom doc models for memory efficiency in exchange of versatility
- fast is its only advantage, and it kind of sucks at everything else
- it does have a smart input mechanism but it's busy-wait and not guaranteed to be reliable with concurrent input requests
- you need to change images from the predefined images/ directory, hack if different

### Deprecated! 

⚠️ This experiment has been deprecated. However, it's still quite an interesting piece of code!

- Please do not use this, it is garbage. Unless you somehow need a Java UI terminal for exclusively printing UTF8 text at a rate of 2 million lines/s. 
- Put on GitHub to archive the code I spent weeks on before I delete it

### Current limitations (Not fixing them)

- Cannot output colored text (as you can see, errors use the ❌ emoji)
- Doesn't have H scrollbar or linewrap (actually used to but lazy to implement)
- Can't be instantiated
- Fixed title is drawn through images

> a Maradona experiment by WillUHD, 2025. All code in this repository is written by WillUHD, previously affiliated with DeepField which is related to [another individual](https://github.com/Ziqian-Huang0607)
>
> peace 
