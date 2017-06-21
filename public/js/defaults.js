$(document).ready(function() {
    var resetCheckbox = function(elem,target) {
        var $draggable = $(elem);
        $draggable.detach().css({top: 0,left: 0}).appendTo(this);
        var shouldShow = $(target).hasClass('target');
        $toggle = $draggable.find(".toggle");
        if(shouldShow) {
            $toggle.show();
        } else {
            $toggle.hide();
        }
        var $checkbox = $draggable.find(".checkbox")
        $checkbox.prop("checked", shouldShow);
        $checkbox.prop("disabled", !shouldShow);
    }

    var dropFunc = function(event, ui) {
        resetCheckbox(ui.draggable,this);
    };

    $('.draggable').draggable({
        revert: true
    });

    $('.droppable.filters').droppable({
        accept: '.draggable.filters',
        drop: dropFunc
    });

    $(".checkbox").on("click", function (e) {
        var checkbox = $(this);
        // do the confirmation thing here
        e.preventDefault();
        return false;
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

    $('.draggable').dblclick(function() {
        var id = $(this).data('target');
        if(id) {
            var target;
            if($(this).closest('.droppable').hasClass('target')) {
                target = "start";
            } else {
                target = "target";
            }
            $target = $('#'+id+'-'+target);
            if($target) {
                  resetCheckbox(this,$('#'+id+));
            }
        }
    });

});
