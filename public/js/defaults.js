$(document).ready(function() {
    var dropFunc = function(event, ui) {
        var $draggable = $(ui.draggable);
        $draggable.detach().css({top: 0,left: 0}).appendTo(this);
        $toggle = $draggable.find(".toggle");
        $toggle.toggle();
        var shouldCheck = $toggle.is(":visible");
        var $checkbox = $draggable.find(".checkbox")
        $checkbox.prop("checked", shouldCheck);
    };

    $('.draggable').draggable({
        revert: true
    });

    $('.droppable.filters').droppable({
        accept: '.draggable.filters',
        drop: dropFunc
    });

    $('.droppable.values').droppable({
        accept: '.draggable.values',
        drop: dropFunc
    });

    $('.droppable.attributes').droppable({
        accept: '.draggable.attributes',
        drop: dropFunc
    });

    $('.droppable.charts').droppable({
        accept: '.draggable.charts',
        drop: dropFunc
    });

});
