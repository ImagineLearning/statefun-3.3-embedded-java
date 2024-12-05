      exports.handler = async (event) => {
          console.log(event);
          const response = {
              statusCode: 200,
              event: event
          };
          return response;
      };
