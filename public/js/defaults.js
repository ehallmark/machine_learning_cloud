var dropFunc = function(event, ui) {
    $(ui.draggable).detach().css({top: 0,left: 0}).appendTo(this);
};

$('.draggable').draggable();

#('.droppable.filters').droppable({
    accept: '.draggable.filters',
    drop: dropFunc
});

#('.droppable.values').droppable({
    accept: '.draggable.values',
    drop: dropFunc
});

#('.droppable.attributes').droppable({
    accept: '.draggable.attributes',
    drop: dropFunc
});