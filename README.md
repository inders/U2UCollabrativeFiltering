Basic Requirements
Implement scoring in this class as follows:

##Use user-user collaborative filtering.

1. Compute user similarities by taking the cosine between the users’ mean-centered rating vectors. LensKit’s CosineVectorSimilarity class can help you with this.
2. For each item’s score, use the 30 most similar users who have rated the item.
3. Use mean-centering to normalize ratings for scoring. That is, compute the weighted average of each neighbor v’s offset from average (rv,i−μv), then add the user’s average rating μu. Like this, where N(u;i) is the neighbors of u for item i:

pu,i=μu+∑v∈N(u;i)s(u,v)(rv,i−μv)∑v∈N(u;i)|s(u,v)|

Command:
/bin/sh target/bin/run-uu 1024:77 1024:268 1024:462 1024:393 1024:36955 2048:77 2048:36955 2048:788
Output:
2048,788,3.8509,Mrs. Doubtfire (1993)
2048,36955,3.9698,True Lies (1994)
2048,77,4.8493,Memento (2000)
1024,462,3.1082,Erin Brockovich (2000)
1024,393,3.8722,Kill Bill: Vol. 2 (2004)
1024,36955,2.3524,True Lies (1994)
1024,77,4.3848,Memento (2000)
1024,268,2.8646,Batman (1989)